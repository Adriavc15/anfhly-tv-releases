package com.anfhly.streaming.data.repository

import android.content.Context
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.WatchlistItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

class FavoritesRepository(private val context: Context) {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        const val USERS_COLLECTION = "users"
        const val WATCHLIST_COLLECTION = "watchlist"
    }
    
    /**
     * Get all favorites for current profile as Flow
     */
    fun getFavoritesForProfile(userId: String, profileId: String): Flow<List<WatchlistItem>> {
        return kotlinx.coroutines.flow.flow {
            try {
                android.util.Log.d("FavoritesRepository", "Getting favorites for userId: $userId, profile: $profileId")
                
                // IMPORTANTE: Como en la versión web, profileId es en realidad el userId de Firebase
                // El userId parámetro es el auth UID, pero profileId es el ID que se usa en Firestore
                val firestoreUserId = if (profileId.isNotEmpty() && profileId != "default") profileId else userId
                
                android.util.Log.d("FavoritesRepository", "Using firestoreUserId: $firestoreUserId")
                
                // Validaciones iniciales
                if (firestoreUserId.isEmpty()) {
                    android.util.Log.e("FavoritesRepository", "FirestoreUserId is empty")
                    emit(emptyList())
                    return@flow
                }
                
                // Check if Firebase is initialized
                if (firestore == null) {
                    android.util.Log.e("FavoritesRepository", "Firestore is null")
                    emit(emptyList())
                    return@flow
                }
                
                // Fetch from web structure: users/{userId}/watchlist
                val snapshot = try {
                    android.util.Log.d("FavoritesRepository", "Attempting to fetch from: $USERS_COLLECTION/$firestoreUserId/$WATCHLIST_COLLECTION")
                    
                    val result = firestore.collection(USERS_COLLECTION)
                        .document(firestoreUserId) // Usar firestoreUserId como en la web
                        .collection(WATCHLIST_COLLECTION)
                        .orderBy("addedAt", Query.Direction.DESCENDING)
                        .get()
                        .await()
                    
                    android.util.Log.d("FavoritesRepository", "Successfully fetched ${result.documents.size} documents")
                    result
                } catch (e: Exception) {
                    android.util.Log.e("FavoritesRepository", "Error fetching favorites from web structure", e)
                    emptyList<com.google.firebase.firestore.QuerySnapshot>()
                }
                
                // Handle both QuerySnapshot and empty list case
                val favorites = if (snapshot is com.google.firebase.firestore.QuerySnapshot) {
                    android.util.Log.d("FavoritesRepository", "Processing QuerySnapshot with ${snapshot.documents.size} documents")
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data
                            android.util.Log.d("FavoritesRepository", "Parsing document: ${doc.id}, data: $data")
                            
                            WatchlistItem(
                                id = doc.id, // Document ID
                                tmdbId = doc.getLong("tmdbId")?.toInt() ?: doc.getString("id")?.toIntOrNull() ?: 0,
                                firebaseId = doc.getString("firebaseId") ?: doc.id,
                                title = doc.getString("title"),
                                name = doc.getString("name"),
                                type = doc.getString("type") ?: "",
                                posterPath = doc.getString("posterPath") ?: "",
                                posterUrl = doc.getString("posterUrl") ?: "",
                                backdropPath = doc.getString("backdropPath"),
                                backdropUrl = doc.getString("backdropUrl"),
                                overview = doc.getString("overview"),
                                vote_average = doc.getDouble("vote_average"),
                                release_date = doc.getString("release_date"),
                                first_air_date = doc.getString("first_air_date"),
                                addedAt = doc.getTimestamp("addedAt")?.toDate(),
                                profileId = profileId
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("FavoritesRepository", "Error parsing document ${doc.id}", e)
                            null
                        }
                    }
                } else {
                    android.util.Log.d("FavoritesRepository", "Snapshot is not QuerySnapshot, returning empty list")
                    emptyList<WatchlistItem>()
                }
                
                android.util.Log.d("FavoritesRepository", "Successfully parsed ${favorites.size} favorites")
                emit(favorites)
                
            } catch (e: Exception) {
                android.util.Log.e("FavoritesRepository", "Critical error in getFavoritesForProfile", e)
                emit(emptyList())
            }
        }
    }
    
    /**
     * Check if a content is in favorites for current user
     */
    fun isFavorite(userId: String, profileId: String, contentId: String): Flow<Boolean> {
        return kotlinx.coroutines.flow.flow {
            try {
                val currentUserId = if (userId.isNotEmpty()) userId else auth.currentUser?.uid
                if (currentUserId == null) {
                    emit(false)
                    return@flow
                }
                
                // Consulta simple sin listener para evitar problemas de composición
                // Usar userId como en la web
                val snapshot = firestore.collection(USERS_COLLECTION)
                    .document(currentUserId)
                    .collection(WATCHLIST_COLLECTION)
                    .document(contentId)
                    .get()
                    .await()
                
                val isFav = snapshot.exists()
                android.util.Log.d("FavoritesRepository", "Favorite status for $contentId: $isFav")
                emit(isFav)
            } catch (e: Exception) {
                android.util.Log.e("FavoritesRepository", "Error checking favorite status", e)
                emit(false)
            }
        }
    }
    
    /**
     * Add a movie to favorites for specific profile
     */
    suspend fun addMovie(movie: Content.Movie, userId: String, profileId: String) {
        try {
            val currentUserId = if (userId.isNotEmpty()) userId else auth.currentUser?.uid
            if (currentUserId == null) {
                throw Exception("No user ID provided and no current user")
            }
            
            val watchlistItem = mapOf(
                "id" to movie.id.toString(),
                "tmdbId" to (movie.id.toIntOrNull() ?: 0),
                "firebaseId" to movie.id.toString(),
                "title" to movie.title,
                "name" to null,
                "type" to "movie",
                "posterPath" to movie.posterUrl?.let { 
                    if (it.contains("tmdb.org")) it.substringAfter("w500") 
                    else null 
                },
                "posterUrl" to movie.posterUrl,
                "backdropPath" to movie.backdropUrl?.let { 
                    if (it.contains("tmdb.org")) it.substringAfter("original") 
                    else null 
                },
                "backdropUrl" to movie.backdropUrl,
                "overview" to movie.overview,
                "vote_average" to movie.voteAverage,
                "release_date" to movie.releaseDate,
                "first_air_date" to null,
                "addedAt" to com.google.firebase.Timestamp.now(),
                "profileId" to profileId
            )
            
            // Create document with specific ID like web version using userId
            android.util.Log.d("FavoritesRepository", "SAVING: userId=$currentUserId, movieId=${movie.id}")
            android.util.Log.d("FavoritesRepository", "SAVING: path=users/$currentUserId/watchlist/${movie.id}")
            android.util.Log.d("FavoritesRepository", "SAVING: data=$watchlistItem")
            
            firestore.collection(USERS_COLLECTION)
                .document(currentUserId) // Usar userId como en la web
                .collection(WATCHLIST_COLLECTION)
                .document(movie.id.toString())
                .set(watchlistItem)
                .await()
                
            android.util.Log.d("FavoritesRepository", "✅ SUCCESS: Added movie to watchlist: ${movie.title}")
        } catch (e: Exception) {
            throw Exception("Error adding movie to favorites: ${e.message}", e)
        }
    }
    
    /**
     * Add a series to favorites for specific profile
     */
    suspend fun addSeries(series: Content.Series, userId: String, profileId: String) {
        try {
            val currentUserId = if (userId.isNotEmpty()) userId else auth.currentUser?.uid
            if (currentUserId == null) {
                throw Exception("No user ID provided and no current user")
            }
            
            val watchlistItem = mapOf(
                "id" to series.id.toString(),
                "tmdbId" to (series.id.toIntOrNull() ?: 0),
                "firebaseId" to series.id.toString(),
                "title" to null,
                "name" to series.title,
                "type" to "series",
                "posterPath" to series.posterUrl?.let { 
                    if (it.contains("tmdb.org")) it.substringAfter("w500") 
                    else null 
                },
                "posterUrl" to series.posterUrl,
                "backdropPath" to series.backdropUrl?.let { 
                    if (it.contains("tmdb.org")) it.substringAfter("original") 
                    else null 
                },
                "backdropUrl" to series.backdropUrl,
                "overview" to series.overview,
                "vote_average" to series.voteAverage,
                "release_date" to null,
                "first_air_date" to series.releaseDate,
                "addedAt" to com.google.firebase.Timestamp.now(),
                "profileId" to profileId
            )
            
            // Create document with specific ID like web version using userId
            android.util.Log.d("FavoritesRepository", "SAVING: userId=$currentUserId, seriesId=${series.id}")
            android.util.Log.d("FavoritesRepository", "SAVING: path=users/$currentUserId/watchlist/${series.id}")
            android.util.Log.d("FavoritesRepository", "SAVING: data=$watchlistItem")
            
            firestore.collection(USERS_COLLECTION)
                .document(currentUserId) // Usar userId como en la web
                .collection(WATCHLIST_COLLECTION)
                .document(series.id.toString())
                .set(watchlistItem)
                .await()
                
            android.util.Log.d("FavoritesRepository", "✅ SUCCESS: Added series to watchlist: ${series.title}")
        } catch (e: Exception) {
            throw Exception("Error adding series to favorites: ${e.message}", e)
        }
    }
    
    /**
     * Remove from favorites for specific profile
     */
    suspend fun removeFavorite(contentId: String, userId: String, profileId: String) {
        try {
            val currentUserId = if (userId.isNotEmpty()) userId else auth.currentUser?.uid
            if (currentUserId == null) {
                throw Exception("No user ID provided and no current user")
            }
            
            firestore.collection(USERS_COLLECTION)
                .document(currentUserId) // Usar userId como en la web
                .collection(WATCHLIST_COLLECTION)
                .document(contentId)
                .delete()
                .await()
        } catch (e: Exception) {
            throw Exception("Error removing from favorites: ${e.message}", e)
        }
    }
    
    /**
     * Toggle favorite status for specific profile (simplified version)
     */
    suspend fun toggleFavorite(content: Content, userId: String, profileId: String) {
        try {
            val currentUserId = if (userId.isNotEmpty()) userId else auth.currentUser?.uid
            if (currentUserId == null) {
                throw Exception("No user ID provided and no current user")
            }
            
            when (content) {
                is Content.Movie -> addMovie(content, currentUserId, profileId)
                is Content.Series -> addSeries(content, currentUserId, profileId)
            }
            
            android.util.Log.d("FavoritesRepository", "Added toggle favorite: ${content.title}")
        } catch (e: Exception) {
            // Si ya existe, intentar eliminarlo
            try {
                val currentUserIdForRemove = if (userId.isNotEmpty()) userId else auth.currentUser?.uid
                val contentId = when (content) {
                    is Content.Movie -> content.id.toString()
                    is Content.Series -> content.id.toString()
                }
                if (currentUserIdForRemove != null) {
                    removeFavorite(contentId, currentUserIdForRemove, profileId)
                    android.util.Log.d("FavoritesRepository", "Removed toggle favorite: ${content.title}")
                }
            } catch (removeError: Exception) {
                throw Exception("Error toggling favorite status: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get favorite item by ID for specific profile
     */
    suspend fun getFavorite(contentId: String, userId: String, profileId: String): WatchlistItem? {
        return try {
            val currentUserId = if (userId.isNotEmpty()) userId else auth.currentUser?.uid
            if (currentUserId == null) {
                return null
            }
            
            val doc = firestore.collection(USERS_COLLECTION)
                .document(profileId) // Usar profileId como en la web
                .collection(WATCHLIST_COLLECTION)
                .document(contentId)
                .get()
                .await()
            
            if (!doc.exists()) null else {
                WatchlistItem(
                    id = doc.id,
                    tmdbId = doc.getLong("tmdbId")?.toInt() ?: doc.getString("id")?.toIntOrNull() ?: 0,
                    firebaseId = doc.getString("firebaseId") ?: doc.id,
                    title = doc.getString("title"),
                    name = doc.getString("name"),
                    type = doc.getString("type") ?: "",
                    posterPath = doc.getString("posterPath") ?: "",
                    posterUrl = doc.getString("posterUrl") ?: "",
                    backdropPath = doc.getString("backdropPath"),
                    backdropUrl = doc.getString("backdropUrl"),
                    overview = doc.getString("overview"),
                    vote_average = doc.getDouble("vote_average"),
                    release_date = doc.getString("release_date"),
                    first_air_date = doc.getString("first_air_date"),
                    addedAt = doc.getTimestamp("addedAt")?.toDate(),
                    profileId = profileId
                )
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Clear all favorites for specific profile
     */
    suspend fun clearAllFavorites(userId: String, profileId: String) {
        try {
            val currentUserId = if (userId.isNotEmpty()) userId else auth.currentUser?.uid
            if (currentUserId == null) {
                throw Exception("No user ID provided and no current user")
            }
            
            val allDocs = firestore.collection(USERS_COLLECTION)
                .document(currentUserId)
                .collection(WATCHLIST_COLLECTION)
                .get()
                .await()
            
            for (document in allDocs.documents) {
                document.reference.delete().await()
            }
        } catch (e: Exception) {
            throw Exception("Error clearing all favorites: ${e.message}", e)
        }
    }
    
    /**
     * Debug method to explore Firestore structure (using userId)
     */
    suspend fun debugFirestoreStructure(userId: String) {
        try {
            android.util.Log.d("FavoritesRepository", "=== DEBUG: Exploring Firestore Structure ===")
            android.util.Log.d("FavoritesRepository", "Using userId: $userId")
            
            if (userId.isEmpty()) {
                android.util.Log.e("FavoritesRepository", "Cannot debug: userId is empty")
                return
            }
            
            // Check if users collection exists
            val usersCollection = firestore.collection(USERS_COLLECTION).get().await()
            android.util.Log.d("FavoritesRepository", "Users collection exists: ${usersCollection.metadata.isFromCache}")
            android.util.Log.d("FavoritesRepository", "Users collection size: ${usersCollection.documents.size}")
            
            // List all user documents
            usersCollection.documents.forEach { doc ->
                android.util.Log.d("FavoritesRepository", "User document: ${doc.id} - exists: ${doc.exists()}")
                if (doc.exists()) {
                    android.util.Log.d("FavoritesRepository", "User data: ${doc.data}")
                }
            }
            
            // Check specific user
            val userDoc = firestore.collection(USERS_COLLECTION).document(userId).get().await()
            android.util.Log.d("FavoritesRepository", "User $userId exists: ${userDoc.exists()}")
            if (userDoc.exists()) {
                android.util.Log.d("FavoritesRepository", "User data: ${userDoc.data}")
            }
            
            // Try direct access to watchlist
            val watchlistSnapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(WATCHLIST_COLLECTION)
                .get()
                .await()
            
            android.util.Log.d("FavoritesRepository", "Watchlist collection size: ${watchlistSnapshot.documents.size}")
            watchlistSnapshot.documents.forEach { doc ->
                android.util.Log.d("FavoritesRepository", "Watchlist item: ${doc.id} - data: ${doc.data}")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("FavoritesRepository", "Error in debugFirestoreStructure", e)
        }
    }
}

