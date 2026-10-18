package com.anfhly.streaming.data.repository

import android.util.Log
import com.anfhly.streaming.data.model.CastMember
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.ContentCategory
import com.anfhly.streaming.data.model.Episode
import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.model.Season
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date
import java.util.concurrent.TimeUnit

class ContentRepository {
    
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "ContentRepository"
    private val tmdbApiClient = TmdbApiClient()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    /**
     * TMDB Genre ID to name mapping
     */
    private val tmdbGenreMap = mapOf(
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
        37 to "Western"
    )
    
    /**
     * Get genre names from TMDB genre IDs
     */
    private fun getGenreNamesFromIds(genreIds: List<Int>?): List<String> {
        if (genreIds.isNullOrEmpty()) return emptyList()
        return genreIds.mapNotNull { tmdbGenreMap[it] }
    }
    
    /**
     * Extract genres from Firestore document - handles String names, Int IDs, and array of maps
     */
    private fun extractGenres(doc: DocumentSnapshot): List<String> {
        val genres = mutableListOf<String>()

        // Try to get genre_ids (TMDB numeric IDs)
        val genreIdsRaw = doc.get("genre_ids")
        if (genreIdsRaw != null) {
            val genreIds = when (genreIdsRaw) {
                is List<*> -> genreIdsRaw.filterIsInstance<Number>().map { it.toInt() }
                else -> emptyList()
            }
            if (genreIds.isNotEmpty()) {
                genres.addAll(getGenreNamesFromIds(genreIds))
            }
        }

        // Try to get genres as array of maps (Firestores structure: [{id: 18, name: "Drama"}, ...])
        val genresRaw = doc.get("genres")
        if (genresRaw != null && genresRaw is List<*>) {
            genresRaw.forEach { item ->
                when (item) {
                    is String -> {
                        // Direct string genre
                        if (genres.none { it.equals(item, ignoreCase = true) }) {
                            genres.add(item)
                        }
                    }
                    is Map<*, *> -> {
                        // Map with id and name
                        val name = item["name"] as? String
                        if (name != null && genres.none { it.equals(name, ignoreCase = true) }) {
                            genres.add(name)
                        }
                    }
                }
            }
        }

        return genres
    }

    /**
     * Construye URL de foto de perfil (TMDB o URL absoluta en Firestore).
     */
    private fun profileImageUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http", ignoreCase = true)) return path
        if (path.startsWith("/")) return "https://image.tmdb.org/t/p/w342$path"
        return buildImageUrl(path, "w342")
    }

    /**
     * Helper to safely extract dates whether they are Timestamps, Strings, or Longs
     */
    private fun getSafeDate(doc: DocumentSnapshot, vararg fields: String): Date? {
        for (field in fields) {
            try {
                // 1. Intentar como Timestamp directo
                val date = doc.getDate(field)
                if (date != null) return date

                // 2. Intentar como Long (milisegundos)
                val timestamp = doc.getLong(field)
                if (timestamp != null) return Date(timestamp)

                // 3. Intentar como String
                val str = doc.getString(field)
                if (!str.isNullOrBlank()) {
                    try {
                        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                        return format.parse(str)
                    } catch (e: Exception) {
                        try {
                            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            return format.parse(str)
                        } catch (e2: Exception) {
                            // Ignorar y seguir
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing date field $field: ${e.message}")
            }
        }
        return null
    }

    /**
     * Lee el array `cast` del documento: objetos estilo TMDB o lista de nombres.
     */
    private fun parseCastMembers(doc: DocumentSnapshot): List<CastMember> {
        val raw = doc.get("cast") ?: return emptyList()
        if (raw !is List<*>) return emptyList()
        return raw.mapNotNull { item ->
            when (item) {
                is String -> {
                    val n = item.trim()
                    if (n.isEmpty()) null else CastMember(name = n)
                }
                is Map<*, *> -> {
                    val name = (item["name"] as? String)?.trim().orEmpty()
                    if (name.isEmpty()) return@mapNotNull null
                    val character = (item["character"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: (item["role"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    val path = item["profile_path"] as? String ?: item["profilePath"] as? String
                    CastMember(
                        name = name,
                        character = character,
                        profileUrl = profileImageUrl(path)
                    )
                }
                else -> null
            }
        }
    }
    
    /**
     * Get all movies with correct Firestore field names
     */
    suspend fun getAllMovies(): Result<List<Content.Movie>> {
        return try {
            Log.d(TAG, "Loading movies from Firestore...")
            val snapshot = firestore.collection("movies")
                .get()
                .await()
            
            Log.d(TAG, "Found ${snapshot.documents.size} movies in Firestore")
            
            val movies = snapshot.documents.mapNotNull { doc ->
                try {
                    val filemoonUrl = doc.getString("filemoonUrl") ?: doc.getString("filemoon_url")
                    val easyvidplayerUrl = doc.getString("easyvidplayerUrl") ?: doc.getString("easyvidplayer_url")
                    val videoUrl = doc.getString("fileUrl") ?: doc.getString("videoUrl") ?: doc.getString("video_url")
                    
                    if (videoUrl.isNullOrBlank() && filemoonUrl.isNullOrBlank() && easyvidplayerUrl.isNullOrBlank()) {
                        Log.d(TAG, "Skipping movie ${doc.id}: no valid video URL")
                        return@mapNotNull null
                    }
                    
                    // Usar videoUrl directamente sin verificación HTTP (la verificación se hace al reproducir)
                    // Filemoon desactivado - ya no usar como fallback
                    val finalVideoUrl = videoUrl ?: easyvidplayerUrl

                    val castMembers = parseCastMembers(doc)
                    
                    // Get poster URL - Extract poster and backdrop URLs from TMDB paths only
                    val posterPath = doc.getString("poster_path")
                    val backdropPath = doc.getString("backdrop_path")
                    val backdropPublicityPath = doc.getString("backdropublicity_path")
                    val logoPath = doc.getString("logo_path")
                    
                    // Build full URLs
                    val posterUrl = buildImageUrl(posterPath, "w500")
                    val backdropUrl = buildImageUrl(backdropPath, "original")
                    // Si backdropPublicityPath ya es una URL completa, usarla directamente
                    val backdropPublicityUrl = if (backdropPublicityPath?.startsWith("http") == true) {
                        backdropPublicityPath
                    } else {
                        buildImageUrl(backdropPublicityPath, "original")
                    }
                    val logoUrl = buildImageUrl(logoPath, "w500")
                    
                    // Get localized overview with priority: es > es-MX > en > zh > ja > default
                    val overview = doc.getString("overview_es")
                        ?: doc.getString("overview_es_mx")
                        ?: doc.getString("overview_es-MX")
                        ?: doc.getString("overview_en")
                        ?: doc.getString("overview_zh")
                        ?: doc.getString("overview_ja")
                        ?: doc.getString("overview")
                        ?: ""
                    
                    Content.Movie(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        overview = overview,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        backdropPublicityUrl = backdropPublicityUrl,
                        voteAverage = getSafeDouble(doc, "rating", "voteAverage", "vote_average"),
                        releaseDate = doc.getString("releaseDate") ?: doc.getString("release_date"),
                        genres = extractGenres(doc),
                        isPremium = false,
                        logoUrl = logoUrl,
                        customPosterUrl = buildImageUrl(doc.getString("customPosterUrl"), "w500"),
                        customBackdropUrl = buildImageUrl(doc.getString("customBackdropUrl"), "original"),
                        customLogoUrl = buildImageUrl(doc.getString("customLogoUrl"), "w500") ?: doc.getString("customLogoUrl"),
                        customBackdropUrlBluray = buildImageUrl(doc.getString("customBackdropUrlBluray"), "original"),
                        customBackdropUrlImax = buildImageUrl(doc.getString("customBackdropUrlImax"), "original"),
                        customBackdropUrlOpenmatte = buildImageUrl(doc.getString("customBackdropUrlOpenmatte"), "original"),
                        customBackdropUrl43 = buildImageUrl(doc.getString("customBackdropUrl43"), "original"),
                        runtime = doc.getLong("runtime")?.toInt(),
                        videoUrl = finalVideoUrl,
                        fileUrl = doc.getString("fileUrl"),
                        fileUrlImax = doc.getString("fileUrlImax"),
                        fileUrlOpenmatte = doc.getString("fileUrlOpenmatte"),
                        fileUrl43 = doc.getString("fileUrl43"),
                        filemoonUrl = filemoonUrl,
                        easyvidplayerUrl = easyvidplayerUrl,
                        tmdbId = doc.getLong("tmdbId")?.toInt() ?: doc.getLong("id")?.toInt(),
                        trailerUrl = doc.getString("trailerUrl") ?: doc.getString("trailer_url"),
                        addedAt = getSafeDate(doc, "uploadedAt", "addedAt", "createdAt"),
                        repairedAt = getSafeDate(doc, "repairedAt", "fixedAt"),
                        updatedAt = getSafeDate(doc, "updatedAt"),
                        certification = doc.getString("certification"),
                        platform = doc.getString("platform") ?: doc.getString("plataforma"),
                        castMembers = castMembers,
                        cast = castMembers.map { it.name }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing movie ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "Successfully loaded ${movies.size} movies")
            Result.Success(movies)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading movies", e)
            Result.Error(e)
        }
    }
    
    /**
     * Return image URL as-is without any conversion
     */
    private fun buildImageUrl(path: String?, size: String): String? {
        // Return URL as-is, no conversion
        return path
    }
    
    /**
     * Check if a Filemoon URL is accessible (returns 200 OK)
     */
    private suspend fun isFilemoonUrlActive(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            
            val response = httpClient.newCall(request).execute()
            val isActive = response.isSuccessful
            response.close()
            
            Log.d(TAG, "Filemoon URL check: $url - Active: $isActive (code: ${response.code})")
            isActive
        } catch (e: Exception) {
            Log.d(TAG, "Filemoon URL check failed: $url - ${e.message}")
            false
        }
    }
    
    /**
     * Construye [Content.Series] desde un documento Firestore (sin usar toObject, compatible con `cast` como array de mapas).
     */
    private fun parseSeriesDocument(doc: DocumentSnapshot): Content.Series? {
        return try {
            val posterPath = doc.getString("poster_path")
            val backdropPath = doc.getString("backdrop_path")
            val backdropPublicityPath = doc.getString("backdropublicity_path")
            val logoPath = doc.getString("logo_path")

            val posterUrl = buildImageUrl(posterPath, "w500")
            val backdropUrl = buildImageUrl(backdropPath, "original")
            val backdropPublicityUrl = if (backdropPublicityPath?.startsWith("http") == true) {
                backdropPublicityPath
            } else {
                buildImageUrl(backdropPublicityPath, "original")
            }
            val logoUrl = buildImageUrl(logoPath, "w500")

            val seasonsData = doc.get("seasons") as? List<Map<String, Any>>
            val seasons = seasonsData?.map { seasonMap ->
                Season(
                    id = seasonMap["id"].toString(),
                    seriesId = doc.id,
                    seasonNumber = (seasonMap["season_number"] as? Number)?.toInt() ?: 0,
                    name = seasonMap["name"] as? String ?: "",
                    overview = seasonMap["overview"] as? String ?: "",
                    posterUrl = buildImageUrl(seasonMap["poster_path"] as? String, "w500"),
                    episodeCount = (seasonMap["episode_count"] as? Number)?.toInt() ?: 0,
                    airDate = seasonMap["air_date"] as? String
                )
            } ?: emptyList()

            val overview = doc.getString("overview_es")
                ?: doc.getString("overview_es_mx")
                ?: doc.getString("overview_es-MX")
                ?: doc.getString("overview_en")
                ?: doc.getString("overview_zh")
                ?: doc.getString("overview_ja")
                ?: doc.getString("overview")
                ?: ""

            val castMembers = parseCastMembers(doc)

            Content.Series(
                id = doc.id,
                title = doc.getString("name") ?: doc.getString("title") ?: "",
                overview = overview,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                backdropPublicityUrl = backdropPublicityUrl,
                voteAverage = getSafeDouble(doc, "rating", "voteAverage", "vote_average"),
                releaseDate = doc.getString("firstAirDate") ?: doc.getString("first_air_date")
                    ?: doc.getString("releaseDate"),
                genres = extractGenres(doc),
                isPremium = false,
                logoUrl = logoUrl,
                customPosterUrl = buildImageUrl(doc.getString("customPosterUrl"), "w500"),
                customBackdropUrl = buildImageUrl(doc.getString("customBackdropUrl"), "original"),
                customLogoUrl = buildImageUrl(doc.getString("customLogoUrl"), "w500") ?: doc.getString("customLogoUrl"),
                customBackdropUrlBluray = buildImageUrl(doc.getString("customBackdropUrlBluray"), "original"),
                customBackdropUrlImax = buildImageUrl(doc.getString("customBackdropUrlImax"), "original"),
                customBackdropUrlOpenmatte = buildImageUrl(doc.getString("customBackdropUrlOpenmatte"), "original"),
                customBackdropUrl43 = buildImageUrl(doc.getString("customBackdropUrl43"), "original"),
                seasons = seasons,
                seasonCount = seasons.size,
                episodeCount = seasons.sumOf { it.episodeCount },
                tmdbId = doc.getLong("tmdbId")?.toInt() ?: doc.getLong("id")?.toInt(),
                trailerUrl = doc.getString("trailerUrl") ?: doc.getString("trailer_url"),
                isCustomPoster = doc.getBoolean("isCustomPoster") ?: false,
                addedAt = getSafeDate(doc, "uploadedAt", "addedAt", "createdAt"),
                repairedAt = getSafeDate(doc, "repairedAt", "fixedAt"),
                updatedAt = getSafeDate(doc, "updatedAt"),
                certification = doc.getString("certification"),
                platform = doc.getString("platform") ?: doc.getString("plataforma"),
                castMembers = castMembers,
                cast = castMembers.map { it.name }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing series ${doc.id}", e)
            null
        }
    }

    /**
     * Get all series with correct Firestore field names
     */
    suspend fun getAllSeries(): Result<List<Content.Series>> {
        return try {
            Log.d(TAG, "Loading series from Firestore...")
            val snapshot = firestore.collection("series")
                .get()
                .await()
            
            Log.d(TAG, "Found ${snapshot.documents.size} series in Firestore")
            
            val series = snapshot.documents.mapNotNull { doc -> parseSeriesDocument(doc) }

            Log.d(TAG, "Successfully loaded ${series.size} series")
            Result.Success(series)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading series", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get all live TV channels from Firestore mapped to Content.Movie
     */
    suspend fun getAllChannels(): Result<List<Content.Movie>> {
        return try {
            Log.d(TAG, "Loading channels from Firestore...")
            val snapshot = firestore.collection("channels")
                .get()
                .await()
            
            Log.d(TAG, "Found ${snapshot.documents.size} channels in Firestore")
            
            val channels = snapshot.documents.mapNotNull { doc ->
                try {
                    val fileUrl = doc.getString("fileUrl") ?: doc.getString("currentUrl") ?: doc.getString("videoUrl")
                    
                    if (fileUrl.isNullOrBlank()) {
                        Log.d(TAG, "Skipping channel ${doc.id}: no valid URL")
                        return@mapNotNull null
                    }
                    
                    val title = doc.getString("title") ?: doc.getString("name") ?: ""
                    val overview = doc.getString("overview") ?: "TV en Vivo"
                    val genre = doc.getString("genre") ?: "Canales"
                    val posterPath = doc.getString("poster_path") ?: doc.getString("posterUrl")
                    val backdropPath = doc.getString("backdrop_path") ?: doc.getString("backdropUrl")
                    val logoPath = doc.getString("logo_path") ?: doc.getString("logoUrl")
                    
                    Content.Movie(
                        id = doc.id,
                        title = title,
                        overview = overview,
                        posterUrl = posterPath,
                        backdropUrl = backdropPath,
                        backdropPublicityUrl = backdropPath,
                        voteAverage = 0.0,
                        releaseDate = null,
                        genres = listOf(genre),
                        isPremium = false,
                        logoUrl = logoPath,
                        videoUrl = fileUrl,
                        addedAt = getSafeDate(doc, "uploadedAt", "createdAt")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing channel ${doc.id}", e)
                    null
                }
            }
            
            Log.d(TAG, "Successfully loaded ${channels.size} channels")
            Result.Success(channels)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading channels", e)
            Result.Error(e)
        }
    }

    
    /**
     * Get seasons for a series - desde el array 'seasons' en el documento de la serie
     */
    suspend fun getSeasonsForSeries(seriesId: String): Result<List<Season>> {
        return try {
            Log.d(TAG, "getSeasonsForSeries: seriesId=$seriesId")
            
            val doc = firestore.collection("series").document(seriesId).get().await()
            
            if (!doc.exists()) {
                Log.e(TAG, "Series document not found: $seriesId")
                return Result.Error(Exception("Series not found"))
            }
            
            Log.d(TAG, "Series document found, checking for seasons array")
            
            // Log all fields in the document for debugging
            doc.data?.keys?.let { keys ->
                Log.d(TAG, "Document fields: $keys")
            }
            
            // Leer temporadas desde el array 'seasons' en el documento (como la versión web)
            val seasonsRaw = doc.get("seasons")
            Log.d(TAG, "seasons field type: ${seasonsRaw?.javaClass?.simpleName}, value: $seasonsRaw")
            
            val seasonsData = seasonsRaw as? List<Map<String, Any>>
            
            if (seasonsData == null) {
                Log.e(TAG, "No seasons array found or wrong type. Raw value: $seasonsRaw")
                return Result.Success(emptyList())
            }
            
            Log.d(TAG, "Found ${seasonsData.size} seasons in series document")
            
            seasonsData.forEachIndexed { index, seasonMap ->
                Log.d(TAG, "Season $index: $seasonMap")
            }
            
            val seasons = seasonsData.mapIndexed { index, seasonMap ->
                val seasonNumber = (seasonMap["season_number"] as? Number)?.toInt() 
                    ?: (seasonMap["seasonNumber"] as? Number)?.toInt() 
                    ?: index + 1
                val seasonId = seasonMap["id"]?.toString() ?: seasonNumber.toString()
                val seasonName = seasonMap["name"]?.toString() ?: "Temporada ${index + 1}"
                
                Log.d(TAG, "Creating Season: id=$seasonId, number=$seasonNumber, name=$seasonName")
                
                Season(
                    id = seasonId,
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    name = seasonName,
                    overview = seasonMap["overview"]?.toString() ?: "",
                    posterUrl = seasonMap["poster_path"]?.toString() ?: seasonMap["posterUrl"]?.toString(),
                    episodeCount = (seasonMap["episode_count"] as? Number)?.toInt() 
                        ?: (seasonMap["episodeCount"] as? Number)?.toInt() ?: 0,
                    airDate = seasonMap["air_date"]?.toString() ?: seasonMap["airDate"]?.toString()
                )
            }
            
            Log.d(TAG, "Returning ${seasons.size} seasons")
            Result.Success(seasons)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading seasons for series $seriesId", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get episodes for a season - leyendo del array 'episodes' en el documento de la serie
     */
    suspend fun getEpisodesForSeason(seriesId: String, seasonId: String, seasonNumber: Int = 1): Result<List<Episode>> {
        return try {
            Log.d(TAG, "getEpisodesForSeason: seriesId=$seriesId, seasonId=$seasonId, seasonNumber=$seasonNumber")
            
            val doc = firestore.collection("series").document(seriesId).get().await()
            
            if (!doc.exists()) {
                Log.e(TAG, "Series document not found: $seriesId")
                return Result.Success(emptyList())
            }
            
            // Leer episodios desde el array 'episodes' en el documento
            val episodesRaw = doc.get("episodes")
            Log.d(TAG, "episodes field type: ${episodesRaw?.javaClass?.simpleName}")
            
            val episodesData = episodesRaw as? List<Map<String, Any>>
            
            if (episodesData == null) {
                Log.e(TAG, "No episodes array found or wrong type")
                return Result.Success(emptyList())
            }
            
            Log.d(TAG, "Found ${episodesData.size} total episodes in series document")
            
            // Filtrar episodios por season_number
            val filteredEpisodes = episodesData.filter { episodeMap ->
                val epSeasonNumber = (episodeMap["season_number"] as? Number)?.toInt()
                    ?: (episodeMap["seasonNumber"] as? Number)?.toInt()
                    ?: 0
                epSeasonNumber == seasonNumber
            }.sortedBy { episodeMap ->
                (episodeMap["episode_number"] as? Number)?.toInt()
                    ?: (episodeMap["episodeNumber"] as? Number)?.toInt()
                    ?: 0
            }
            
            Log.d(TAG, "Filtered ${filteredEpisodes.size} episodes for season $seasonNumber")
            
            val episodes = filteredEpisodes.mapNotNull { episodeMap ->
                val episodeId = episodeMap["id"]?.toString() ?: ""
                val episodeNumber = (episodeMap["episode_number"] as? Number)?.toInt()
                    ?: (episodeMap["episodeNumber"] as? Number)?.toInt()
                    ?: 0
                
                // Get video URLs
                val filemoonUrl = episodeMap["filemoonUrl"]?.toString() ?: episodeMap["filemoon_url"]?.toString()
                val easyvidplayerUrl = episodeMap["easyvidplayerUrl"]?.toString() ?: episodeMap["easyvidplayer_url"]?.toString()
                val videoUrl = episodeMap["fileUrl"]?.toString()
                
                // Skip episodes with no valid video URL - Filemoon desactivado
                if (videoUrl.isNullOrBlank() && easyvidplayerUrl.isNullOrBlank()) {
                    Log.d(TAG, "Skipping episode $episodeNumber: no valid video URL")
                    return@mapNotNull null
                }
                
                // Usar videoUrl directamente sin verificación HTTP (la verificación se hace al reproducir)
                // Filemoon desactivado - ya no usar como fallback
                val finalVideoUrl = videoUrl ?: easyvidplayerUrl
                
                Episode(
                    id = episodeId,
                    seriesId = seriesId,
                    seasonId = seasonId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    name = episodeMap["name"]?.toString() ?: "Episodio $episodeNumber",
                    overview = episodeMap["overview"]?.toString() ?: "",
                    stillUrl = episodeMap["still_path"]?.toString() ?: episodeMap["stillUrl"]?.toString(),
                    videoUrl = finalVideoUrl,
                    filemoonUrl = filemoonUrl,
                    easyvidplayerUrl = easyvidplayerUrl,
                    runtime = (episodeMap["runtime"] as? Number)?.toInt(),
                    airDate = episodeMap["air_date"]?.toString() ?: episodeMap["airDate"]?.toString(),
                    voteAverage = (episodeMap["vote_average"] as? Number)?.toDouble()
                        ?: (episodeMap["voteAverage"] as? Number)?.toDouble() ?: 0.0
                )
            }
            
            Result.Success(episodes)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading episodes for series $seriesId, season $seasonNumber", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get featured content for carousel from already loaded movies - SOLO ESTRENOS (año vigente)
     * Pass movies list to avoid duplicate Firestore query
     */
    fun getFeaturedContentFromMovies(movies: List<Content.Movie>, count: Int = 10): List<Content> {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        
        return movies.filter { movie ->
            val releaseYear = movie.releaseDate?.take(4)?.toIntOrNull() ?: 0
            releaseYear == currentYear
        }.take(count).shuffled()
    }
    
    /**
     * Get series grouped by genres for Series screen - Using Firestore genres only
     */
    suspend fun getSeriesByGenres(): Result<List<ContentCategory>> {
        return try {
            val seriesResult = getAllSeries()

            if (seriesResult !is Result.Success) {
                return Result.Error(Exception("Failed to load series"))
            }

            val allSeries = seriesResult.data
            Log.d(TAG, "getSeriesByGenres: Total series loaded from Firestore: ${allSeries.size}")

            val categories = mutableListOf<ContentCategory>()

            // Géneros principales a buscar en Firestore
            val targetGenres = listOf(
                "Terror", "Animación", "Suspense", "Drama", "Comedia",
                "Ciencia Ficción", "Acción", "Aventura", "Fantasía", "Crimen",
                "Misterio", "Romance", "Documental", "Familia"
            )

            // 1. Agregadas Recientemente / Nuevos Capítulos (ordenadas por fecha de subida, reparación o actualización de episodios)
            if (allSeries.isNotEmpty()) {
                val recientes = allSeries.sortedByDescending { series ->
                    val added = series.addedAt ?: java.util.Date(0)
                    val repaired = series.repairedAt ?: java.util.Date(0)
                    val updated = series.updatedAt ?: java.util.Date(0)
                    
                    // Encontrar la fecha más reciente
                    maxOf(added, repaired, updated)
                }
                categories.add(ContentCategory("Agregadas Recientemente", recientes))
            }

            // 2. Estrenos (series del año actual ordenadas por fecha)
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val estrenos = allSeries.filter { 
                (it.releaseDate?.take(4)?.toIntOrNull() ?: 0) == currentYear 
            }.sortedByDescending { it.releaseDate ?: "" }
            
            if (estrenos.isNotEmpty()) {
                categories.add(ContentCategory("Estrenos", estrenos))
            }

            // 3. Todas las Series
            if (allSeries.isNotEmpty()) {
                val sortedSeries = allSeries.sortedByDescending { it.releaseDate ?: "" }
                categories.add(ContentCategory("Todas las Series", sortedSeries))
            }

            // Luego: Series por género (usando solo géneros de Firestore)
            targetGenres.forEach { genre ->
                val seriesForGenre = allSeries.filter { series ->
                    series.genres.any { it.equals(genre, ignoreCase = true) }
                }

                if (seriesForGenre.isNotEmpty() && categories.none { it.title == genre }) {
                    categories.add(ContentCategory(genre, seriesForGenre.shuffled().take(30)))
                    Log.d(TAG, "Added category '$genre' with ${seriesForGenre.size} items from Firestore")
                }
            }

            Log.d(TAG, "getSeriesByGenres: Total categories created: ${categories.size}")
            Result.Success(categories)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading series by genres from Firestore", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get movies grouped by genres for Movies screen - Using Firestore genres only
     */
    suspend fun getMoviesByGenres(): Result<List<ContentCategory>> {
        return try {
            val moviesResult = getAllMovies()

            if (moviesResult !is Result.Success) {
                return Result.Error(Exception("Failed to load movies"))
            }

            val allMovies = moviesResult.data
            Log.d(TAG, "getMoviesByGenres: Total movies loaded from Firestore: ${allMovies.size}")

            val categories = mutableListOf<ContentCategory>()

            // Géneros principales a buscar en Firestore
            val targetGenres = listOf(
                "Terror", "Animación", "Suspense", "Drama", "Comedia",
                "Ciencia Ficción", "Acción", "Aventura", "Fantasía", "Crimen",
                "Misterio", "Romance", "Documental", "Familia", "Música",
                "Bélica", "Western"
            )

            // 1. Agregadas/Reparadas Recientemente (ordenadas por fecha de subida, reparación o actualización)
            if (allMovies.isNotEmpty()) {
                val recientes = allMovies.sortedByDescending { movie ->
                    val added = movie.addedAt ?: Date(0)
                    val repaired = movie.repairedAt ?: Date(0)
                    val updated = movie.updatedAt ?: Date(0)
                    maxOf(added, repaired, updated)
                }.take(30)
                categories.add(ContentCategory("Agregadas Recientemente", recientes))
            }

            // 2. Estrenos (películas del año actual)
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val estrenos = allMovies.filter { 
                (it.releaseDate?.take(4)?.toIntOrNull() ?: 0) == currentYear 
            }.sortedByDescending { it.releaseDate ?: "" }
            
            if (estrenos.isNotEmpty()) {
                categories.add(ContentCategory("Estrenos", estrenos))
            }

            // 3. Todas las Películas
            if (allMovies.isNotEmpty()) {
                categories.add(ContentCategory("Todas las Películas", allMovies.shuffled()))
            }

            // Luego: Películas por género (usando solo géneros de Firestore)
            targetGenres.forEach { genre ->
                val moviesForGenre = allMovies.filter { movie ->
                    movie.genres.any { it.equals(genre, ignoreCase = true) }
                }

                if (moviesForGenre.isNotEmpty() && categories.none { it.title == genre }) {
                    categories.add(ContentCategory(genre, moviesForGenre.shuffled().take(30)))
                    Log.d(TAG, "Added category '$genre' with ${moviesForGenre.size} items from Firestore")
                }
            }
            
            Log.d(TAG, "getMoviesByGenres: Total categories created: ${categories.size}")
            Result.Success(categories)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading movies by genres from TMDB", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get content categories for home screen - NUEVAS SECCIONES
     */
    suspend fun getContentCategories(): Result<List<ContentCategory>> {
        return try {
            val moviesResult = getAllMovies()
            val seriesResult = getAllSeries()
            
            val categories = mutableListOf<ContentCategory>()
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            
            // 1. ESTRENOS - Solo contenido del año vigente
            val estrenos = mutableListOf<Content>()
            if (moviesResult is Result.Success) {
                val estrenosMovies = moviesResult.data.filter { 
                    (it.releaseDate?.take(4)?.toIntOrNull() ?: 0) == currentYear 
                }
                estrenos.addAll(estrenosMovies)
            }
            if (seriesResult is Result.Success) {
                val estrenosSeries = seriesResult.data.filter { 
                    (it.releaseDate?.take(4)?.toIntOrNull() ?: 0) == currentYear 
                }
                estrenos.addAll(estrenosSeries)
            }
            if (estrenos.isNotEmpty()) {
                val estrenosOrdenados = estrenos
                    .sortedByDescending { 
                        when (it) {
                            is Content.Movie -> it.releaseDate
                            is Content.Series -> it.releaseDate
                        } ?: ""
                    }
                    .take(30)
                categories.add(ContentCategory("Estrenos", estrenosOrdenados))
            }
            // 2. TOP 10 EN TENDENCIA TMDB - Cartas horizontales tipo Sports
            try {
                val tmdbRepo = TMDBRepository()
                val trendingResult = tmdbRepo.getTrendingMoviesAndSeries()
                if (trendingResult.isNotEmpty()) {
                    val allMovies = if (moviesResult is Result.Success) moviesResult.data else emptyList()
                    val allSeries = if (seriesResult is Result.Success) seriesResult.data else emptyList()
                    
                    val matchedTrending = trendingResult.mapNotNull { trendingItem ->
                        val tmdbId = when (trendingItem) {
                            is Content.Movie -> trendingItem.tmdbId
                            is Content.Series -> trendingItem.tmdbId
                        }
                        
                        val localMovie = allMovies.find { it.tmdbId != null && it.tmdbId != 0 && it.tmdbId == tmdbId }
                        if (localMovie != null) {
                            localMovie
                        } else {
                            val localSeries = allSeries.find { it.tmdbId != null && it.tmdbId != 0 && it.tmdbId == tmdbId }
                            if (localSeries != null) {
                                localSeries
                            } else {
                                null
                            }
                        }
                    }.take(10)
                    
                    if (matchedTrending.isNotEmpty()) {
                        categories.add(ContentCategory("Tendencia", matchedTrending))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching and mapping TMDB trending in getContentCategories", e)
            }

            // 3. RECIENTEMENTE REPARADOS - Contenido ordenado por fecha de reparación
            val reparados = mutableListOf<Content>()
            if (moviesResult is Result.Success) {
                val reparadosMovies = moviesResult.data.filter { it.repairedAt != null }
                reparados.addAll(reparadosMovies)
            }
            if (seriesResult is Result.Success) {
                val reparadosSeries = seriesResult.data.filter { it.repairedAt != null }
                reparados.addAll(reparadosSeries)
            }
            if (reparados.isNotEmpty()) {
                val reparadosOrdenados = reparados
                    .sortedByDescending { 
                        when (it) {
                            is Content.Movie -> it.repairedAt
                            is Content.Series -> it.repairedAt
                        } ?: java.util.Date(0)
                    }
                    .take(30)
                categories.add(ContentCategory("Recientemente Reparados", reparadosOrdenados))
            }
            
            Result.Success(categories)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading categories", e)
            Result.Error(e)
        }
    }
    
    /**
     * Search content
     */
    suspend fun searchContent(query: String): Result<List<Content>> {
        return try {
            val q = query.trim()
            if (q.isEmpty()) return Result.Success(emptyList())
            
            val cleanQuery = normalizeText(q)
            val allContent = mutableListOf<Content>()
            
            val moviesResult = getAllMovies()
            if (moviesResult is Result.Success) {
                allContent.addAll(moviesResult.data)
            }
            
            val seriesResult = getAllSeries()
            if (seriesResult is Result.Success) {
                allContent.addAll(seriesResult.data)
            }

            val channelsResult = getAllChannels()
            if (channelsResult is Result.Success) {
                allContent.addAll(channelsResult.data)
            }

            val titleStartsWith = mutableListOf<Content>()
            val titleContains = mutableListOf<Content>()
            val castMatches = mutableListOf<Content>()
            val overviewMatches = mutableListOf<Content>()

            for (item in allContent) {
                val cleanTitle = normalizeText(item.title)
                if (cleanTitle.startsWith(cleanQuery)) {
                    titleStartsWith.add(item)
                    continue
                }
                if (cleanTitle.contains(cleanQuery)) {
                    titleContains.add(item)
                    continue
                }
                
                val castList = when (item) {
                    is Content.Movie -> item.cast + item.castMembers.map { it.name }
                    is Content.Series -> item.cast + item.castMembers.map { it.name }
                    else -> emptyList()
                }
                if (castList.any { normalizeText(it).contains(cleanQuery) }) {
                    castMatches.add(item)
                    continue
                }
                
                val cleanOverview = normalizeText(item.overview)
                if (cleanOverview.contains(cleanQuery)) {
                    overviewMatches.add(item)
                }
            }

            titleStartsWith.sortBy { normalizeText(it.title) }
            titleContains.sortBy { normalizeText(it.title) }
            castMatches.sortBy { normalizeText(it.title) }
            overviewMatches.sortBy { normalizeText(it.title) }

            val sortedResults = (titleStartsWith + titleContains + castMatches + overviewMatches).distinctBy { it.id }
            Result.Success(sortedResults)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching content", e)
            Result.Error(e)
        }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ü", "u")
            .replace("ñ", "n")
    }

    /**
     * Películas y series donde el intérprete aparece en `cast` / `castMembers` (datos ya cargados desde Firestore).
     */
    suspend fun getContentByActorName(actorName: String): Result<List<Content>> {
        val q = actorName.trim()
        if (q.isEmpty()) return Result.Success(emptyList())
        return try {
            val out = mutableListOf<Content>()
            when (val moviesResult = getAllMovies()) {
                is Result.Success -> out.addAll(moviesResult.data.filter { contentFeaturesActor(it, q) })
                else -> {}
            }
            when (val seriesResult = getAllSeries()) {
                is Result.Success -> out.addAll(seriesResult.data.filter { contentFeaturesActor(it, q) })
                else -> {}
            }
            out.sortByDescending { contentAddedTime(it) }
            Log.d(TAG, "getContentByActorName: '$q' -> ${out.size} títulos")
            Result.Success(out)
        } catch (e: Exception) {
            Log.e(TAG, "Error getContentByActorName", e)
            Result.Error(e)
        }
    }

    private fun contentAddedTime(c: Content): Long = when (c) {
        is Content.Movie -> c.addedAt?.time ?: 0L
        is Content.Series -> c.addedAt?.time ?: 0L
    }

    private fun castNamesFromContent(c: Content): List<String> {
        val raw = when (c) {
            is Content.Movie -> c.castMembers.map { it.name } + c.cast
            is Content.Series -> c.castMembers.map { it.name } + c.cast
        }
        return raw.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun contentFeaturesActor(content: Content, actorQuery: String): Boolean {
        return actorNameMatchesCast(castNamesFromContent(content), actorQuery)
    }

    private fun actorNameMatchesCast(castNames: List<String>, actorQuery: String): Boolean {
        val q = actorQuery.trim()
        if (q.isEmpty()) return false
        return castNames.any { name ->
            name.equals(q, ignoreCase = true) ||
                name.contains(q, ignoreCase = true) ||
                (q.length >= 4 && name.length >= 4 && q.contains(name, ignoreCase = true))
        }
    }
    
    /**
     * Get a single movie by ID
     */
    suspend fun getMovieById(movieId: String): Result<Content.Movie> {
        return try {
            Log.d(TAG, "getMovieById: Loading movie $movieId")
            val doc = firestore.collection("movies").document(movieId).get().await()
            
            if (doc.exists()) {
                val filemoonUrl = doc.getString("filemoonUrl") ?: doc.getString("filemoon_url")
                val easyvidplayerUrl = doc.getString("easyvidplayerUrl") ?: doc.getString("easyvidplayer_url")
                val videoUrl = doc.getString("fileUrl") ?: doc.getString("videoUrl") ?: doc.getString("video_url")
                
                // Usar videoUrl directamente sin verificación HTTP (la verificación se hace al reproducir)
                // Filemoon desactivado - ya no usar como fallback
                val finalVideoUrl = videoUrl ?: easyvidplayerUrl
                
                Log.d(TAG, "Movie $movieId videoUrl: $finalVideoUrl")

                val castMembers = parseCastMembers(doc)
                
                // Get poster URL - Extract poster and backdrop URLs from TMDB paths only
                val posterPath = doc.getString("poster_path")
                val backdropPath = doc.getString("backdrop_path")
                val backdropPublicityPath = doc.getString("backdropublicity_path")
                val logoPath = doc.getString("logo_path")
                
                // Build full URLs
                val posterUrl = buildImageUrl(posterPath, "w500") ?: doc.getString("posterUrl")
                val backdropUrl = buildImageUrl(backdropPath, "original") ?: doc.getString("backdropUrl")
                // Si backdropPublicityPath ya es una URL completa, usarla directamente
                val backdropPublicityUrl = if (backdropPublicityPath?.startsWith("http") == true) {
                    backdropPublicityPath
                } else {
                    buildImageUrl(backdropPublicityPath, "original")
                }
                val logoUrl = buildImageUrl(logoPath, "w500") ?: doc.getString("logoUrl")
                
                // Get localized overview with priority: es > es-MX > en > zh > ja > default
                val overview = doc.getString("overview_es")
                    ?: doc.getString("overview_es_mx")
                    ?: doc.getString("overview_es-MX")
                    ?: doc.getString("overview_en")
                    ?: doc.getString("overview_zh")
                    ?: doc.getString("overview_ja")
                    ?: doc.getString("overview")
                    ?: ""

                val movie = Content.Movie(
                    id = doc.id,
                    title = doc.getString("title") ?: "",
                    overview = overview,
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl,
                    backdropPublicityUrl = backdropPublicityUrl,
                    voteAverage = doc.getDouble("rating") ?: doc.getDouble("voteAverage") ?: doc.getDouble("vote_average") ?: 0.0,
                    releaseDate = doc.getString("releaseDate") ?: doc.getString("release_date"),
                    genres = extractGenres(doc),
                    isPremium = false,
                    logoUrl = logoUrl,
                    customPosterUrl = buildImageUrl(doc.getString("customPosterUrl"), "w500"),
                    customBackdropUrl = buildImageUrl(doc.getString("customBackdropUrl"), "original"),
                    customLogoUrl = buildImageUrl(doc.getString("customLogoUrl"), "w500") ?: doc.getString("customLogoUrl"),
                    customBackdropUrlBluray = buildImageUrl(doc.getString("customBackdropUrlBluray"), "original"),
                    customBackdropUrlImax = buildImageUrl(doc.getString("customBackdropUrlImax"), "original"),
                    customBackdropUrlOpenmatte = buildImageUrl(doc.getString("customBackdropUrlOpenmatte"), "original"),
                    customBackdropUrl43 = buildImageUrl(doc.getString("customBackdropUrl43"), "original"),
                    runtime = doc.getLong("runtime")?.toInt(),
                    videoUrl = finalVideoUrl,
                    fileUrl = doc.getString("fileUrl"),
                    fileUrlImax = doc.getString("fileUrlImax"),
                    fileUrlOpenmatte = doc.getString("fileUrlOpenmatte"),
                    fileUrl43 = doc.getString("fileUrl43"),
                    filemoonUrl = filemoonUrl,
                    easyvidplayerUrl = easyvidplayerUrl,
                    tmdbId = doc.getLong("tmdbId")?.toInt() ?: doc.getLong("id")?.toInt(),
                    trailerUrl = doc.getString("trailerUrl") ?: doc.getString("trailer_url"),
                    addedAt = getSafeDate(doc, "uploadedAt", "addedAt", "createdAt"),
                    repairedAt = getSafeDate(doc, "repairedAt", "fixedAt"),
                    updatedAt = getSafeDate(doc, "updatedAt"),
                    certification = doc.getString("certification"),
                    castMembers = castMembers,
                    cast = castMembers.map { it.name }
                ).also {
                    Log.d(TAG, "Movie $movieId versions -> IMAX: ${it.fileUrlImax}, OpenMatte: ${it.fileUrlOpenmatte}, 4:3: ${it.fileUrl43}")
                }
                Result.Success(movie)
            } else {
                Result.Error(Exception("Movie not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading movie", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get a single series by ID
     */
    suspend fun getSeriesById(seriesId: String): Result<Content.Series> {
        return try {
            val doc = firestore.collection("series").document(seriesId).get().await()
            
            if (doc.exists()) {
                val series = parseSeriesDocument(doc)
                if (series != null) {
                    Result.Success(series)
                } else {
                    Result.Error(Exception("Failed to parse series data"))
                }
            } else {
                Result.Error(Exception("Series not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading series", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get content related by cast members
     * Searches for movies and series that share cast member IDs with the current content
     */
    suspend fun getContentByCast(currentContent: Content): Result<List<Content>> {
        return try {
            Log.d(TAG, "getContentByCast: Searching for content with similar cast for ${currentContent.title}")
            
            val relatedContent = mutableListOf<Content>()
            
            // Get all movies and check for cast overlap
            val moviesResult = getAllMovies()
            if (moviesResult is Result.Success) {
                moviesResult.data.forEach { movie ->
                    if (movie.id != currentContent.id && hasCastOverlap(currentContent, movie)) {
                        relatedContent.add(movie)
                    }
                }
            }
            
            // Get all series and check for cast overlap
            val seriesResult = getAllSeries()
            if (seriesResult is Result.Success) {
                seriesResult.data.forEach { series ->
                    if (series.id != currentContent.id && hasCastOverlap(currentContent, series)) {
                        relatedContent.add(series)
                    }
                }
            }
            
            Log.d(TAG, "getContentByCast: Found ${relatedContent.size} related content items")
            Result.Success(relatedContent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting content by cast", e)
            Result.Error(e)
        }
    }
    
    /**
     * Check if two content items share any cast or crew members
     * Uses ALL cast and crew names for comparison
     */
    private suspend fun hasCastOverlap(content1: Content, content2: Content): Boolean {
        // Get all cast and crew names from each content
        val names1 = getAllCastAndCrewNames(content1)
        val names2 = getAllCastAndCrewNames(content2)
        
        // Return true if there's any overlap in cast/crew names
        val overlap = names1.intersect(names2).isNotEmpty()
        
        if (overlap) {
            val commonNames = names1.intersect(names2)
            Log.d(TAG, "Found cast/crew overlap: ${commonNames.joinToString(", ")} between ${content1.title} and ${content2.title}")
        }
        
        return overlap
    }
    
    /**
     * Extract ALL cast and crew member names from Firestore document
     * Parses both cast and crew arrays and extracts all names
     * Example: ["Tom Hanks", "Tim Allen", "Joan Cusack", "Lee Unkrich", ...] from Toy Story 3
     */
    private suspend fun getAllCastAndCrewNames(content: Content): Set<String> {
        return try {
            // Get the Firestore document for this content
            val collection = if (content is com.anfhly.streaming.data.model.Content.Movie) {
                "movies"
            } else {
                "series"
            }
            
            val doc = firestore.collection(collection).document(content.id).get().await()
            if (doc.exists()) {
                val allNames = mutableSetOf<String>()
                
                // Parse cast array
                val castArray = doc.get("cast") as? List<Map<String, Any>>
                if (castArray != null) {
                    for (castMember in castArray) {
                        val name = castMember["name"] as? String
                        if (!name.isNullOrBlank()) {
                            allNames.add(name)
                        }
                    }
                }
                
                // Parse crew array
                val crewArray = doc.get("crew") as? List<Map<String, Any>>
                if (crewArray != null) {
                    for (crewMember in crewArray) {
                        val name = crewMember["name"] as? String
                        if (!name.isNullOrBlank()) {
                            allNames.add(name)
                        }
                    }
                }
                
                Log.d(TAG, "Found ${allNames.size} cast/crew names for ${content.title}: ${allNames.take(3).joinToString(", ")}${if (allNames.size > 3) "..." else ""}")
                allNames
            } else {
                Log.d(TAG, "Document not found for ${content.title}")
                emptySet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cast/crew names for ${content.title}", e)
            emptySet()
        }
    }

    private fun getSafeDouble(doc: DocumentSnapshot, vararg fields: String): Double {
        for (field in fields) {
            try {
                val value = doc.get(field)
                when (value) {
                    is Number -> return value.toDouble()
                    is String -> {
                        val parsed = value.toDoubleOrNull()
                        if (parsed != null) return parsed
                    }
                }
            } catch (_: Exception) {}
        }
        return 0.0
    }
}
