package com.anfhly.streaming.data.repository

import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.model.WatchlistItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

class WatchlistRepository {
    
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    
    suspend fun getWatchlist(userId: String, profileId: String): Result<List<WatchlistItem>> {
        return try {
            android.util.Log.d("WatchlistRepository", "🔍 Loading watchlist for userId: $userId, profileId: $profileId")
            
            // IMPORTANTE: Como en la versión web, profileId es en realidad el userId de Firebase
            // El userId parámetro es el auth UID, pero profileId es el ID que se usa en Firestore
            val firestoreUserId = if (profileId.isNotEmpty() && profileId != "default") profileId else userId
            
            android.util.Log.d("WatchlistRepository", "📍 Using firestoreUserId: $firestoreUserId")
            
            // Validaciones iniciales
            if (firestoreUserId.isEmpty()) {
                android.util.Log.e("WatchlistRepository", "❌ FirestoreUserId is empty")
                return Result.Error(Exception("FirestoreUserId is empty"))
            }
            
            android.util.Log.d("WatchlistRepository", "📍 Path: users/$firestoreUserId/watchlist")
            
            val snapshot = firestore.collection("users")
                .document(firestoreUserId)
                .collection("watchlist")
                .orderBy("addedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            android.util.Log.d("WatchlistRepository", "✅ Successfully fetched ${snapshot.documents.size} documents")
            
            val items = snapshot.documents.map { doc ->
                android.util.Log.d("WatchlistRepository", "📄 Processing document: ${doc.id}")
                android.util.Log.d("WatchlistRepository", "📄 Document data: ${doc.data}")
                
                val posterPathStr = doc.getString("posterPath") ?: doc.getString("poster_path") ?: ""
                val backdropPathStr = doc.getString("backdropPath") ?: doc.getString("backdrop_path") ?: ""
                
                var posterUrlStr = doc.getString("posterUrl") ?: doc.getString("poster_url") ?: ""
                var backdropUrlStr = doc.getString("backdropUrl") ?: doc.getString("backdrop_url") ?: ""
                
                // If posterUrlStr is empty, fallback to posterPathStr/poster_path
                if (posterUrlStr.isEmpty() && posterPathStr.isNotEmpty()) {
                    posterUrlStr = if (posterPathStr.startsWith("http")) {
                        posterPathStr
                    } else if (posterPathStr.startsWith("/")) {
                        "https://image.tmdb.org/t/p/w500$posterPathStr"
                    } else {
                        posterPathStr
                    }
                } else if (posterUrlStr.isNotEmpty() && !posterUrlStr.startsWith("http") && posterUrlStr.startsWith("/")) {
                    posterUrlStr = "https://image.tmdb.org/t/p/w500$posterUrlStr"
                }
                
                // If backdropUrlStr is empty, fallback to backdropPathStr/backdrop_path
                if (backdropUrlStr.isEmpty() && backdropPathStr.isNotEmpty()) {
                    backdropUrlStr = if (backdropPathStr.startsWith("http")) {
                        backdropPathStr
                    } else if (backdropPathStr.startsWith("/")) {
                        "https://image.tmdb.org/t/p/w1280$backdropPathStr"
                    } else {
                        backdropPathStr
                    }
                } else if (backdropUrlStr.isNotEmpty() && !backdropUrlStr.startsWith("http") && backdropUrlStr.startsWith("/")) {
                    backdropUrlStr = "https://image.tmdb.org/t/p/w1280$backdropUrlStr"
                }
                
                // Extract logo URL with support for multiple formats
                val logoUrlStr = doc.getString("logoUrl") ?: doc.getString("logo_url") ?: doc.getString("logo_path") ?: ""
                val logoUrlVal = if (logoUrlStr.startsWith("http")) {
                    logoUrlStr
                } else if (logoUrlStr.isNotEmpty() && logoUrlStr.startsWith("/")) {
                    "https://image.tmdb.org/t/p/w500$logoUrlStr"
                } else if (logoUrlStr.isNotEmpty()) {
                    logoUrlStr
                } else {
                    null
                }
                
                // Extract backdrop publicity URL with support for multiple formats
                val backdropPublicityUrlStr = doc.getString("backdropPublicityUrl") ?: doc.getString("backdrop_publicity_url") ?: doc.getString("backdropublicity_path") ?: doc.getString("backdrop_publicity_path") ?: ""
                val backdropPublicityUrlVal = if (backdropPublicityUrlStr.startsWith("http")) {
                    backdropPublicityUrlStr
                } else if (backdropPublicityUrlStr.isNotEmpty() && backdropPublicityUrlStr.startsWith("/")) {
                    "https://image.tmdb.org/t/p/original$backdropPublicityUrlStr"
                } else if (backdropPublicityUrlStr.isNotEmpty()) {
                    backdropPublicityUrlStr
                } else {
                    null
                }
                
                val rawId = doc.get("id")?.toString() ?: ""
                val rawFirebaseId = doc.get("firebaseId")?.toString() ?: ""
                
                val item = WatchlistItem(
                    id = doc.id,
                    tmdbId = doc.getLong("tmdbId")?.toInt() ?: rawId.toIntOrNull() ?: 0,
                    firebaseId = rawFirebaseId.ifEmpty { rawId }.ifEmpty { doc.id },
                    title = doc.getString("title"),
                    name = doc.getString("name"),
                    type = doc.getString("type")?.lowercase() ?: "",
                    posterPath = posterPathStr,
                    posterUrl = posterUrlStr,
                    backdropPath = backdropPathStr,
                    backdropUrl = backdropUrlStr,
                    logoUrl = logoUrlVal,
                    backdropPublicityUrl = backdropPublicityUrlVal,
                    overview = doc.getString("overview"),
                    vote_average = doc.getDouble("vote_average"),
                    release_date = doc.getString("release_date"),
                    first_air_date = doc.getString("first_air_date"),
                    addedAt = doc.getTimestamp("addedAt")?.toDate(),
                    profileId = profileId
                )
                
                // Handle legacy items without firebaseId (like web version)
                if (item.firebaseId.isEmpty()) {
                    android.util.Log.w("WatchlistRepository", "⚠️ Legacy item without firebaseId, using id as fallback")
                }
                
                // Handle legacy items without firebaseId (like web version)
                if (item.firebaseId.isEmpty()) {
                    android.util.Log.w("WatchlistRepository", "⚠️ Legacy item without firebaseId, using id as fallback")
                }
                
                android.util.Log.d("WatchlistRepository", "✅ Parsed item: ${item.displayName} (${item.type})")
                item
            }
            
            android.util.Log.d("WatchlistRepository", "✅ Loaded watchlist: ${items.size} items")
            Result.Success(items)
        } catch (e: Exception) {
            android.util.Log.e("WatchlistRepository", "❌ Error loading watchlist", e)
            Result.Error(e)
        }
    }
    
    suspend fun addToWatchlist(userId: String, profileId: String, item: WatchlistItem): Result<String> {
        return try {
            android.util.Log.d("WatchlistRepository", "➕ Adding to watchlist: ${item.displayName} (${item.type})")
            android.util.Log.d("WatchlistRepository", "➕ Item data: id=${item.id}, tmdbId=${item.tmdbId}")
            
            // IMPORTANTE: Como en la versión web, profileId es en realidad el userId de Firebase
            val firestoreUserId = if (profileId.isNotEmpty() && profileId != "default") profileId else userId
            
            android.util.Log.d("WatchlistRepository", "📍 Using firestoreUserId: $firestoreUserId")
            
            // Validaciones iniciales
            if (firestoreUserId.isEmpty()) {
                android.util.Log.e("WatchlistRepository", "❌ FirestoreUserId is empty")
                return Result.Error(Exception("FirestoreUserId is empty"))
            }
            
            android.util.Log.d("WatchlistRepository", "📍 Checking if item already exists: users/$firestoreUserId/watchlist")
            
            val existing = firestore.collection("users")
                .document(firestoreUserId)
                .collection("watchlist")
                .whereEqualTo("id", item.id)
                .get()
                .await()
            
            if (!existing.isEmpty) {
                android.util.Log.w("WatchlistRepository", "⚠️ Item already in watchlist: ${item.displayName}")
                return Result.Error(Exception("Item already in watchlist"))
            }
            
            // Build data like web version
            val data = hashMapOf(
                "id" to item.id,
                "tmdbId" to item.tmdbId,
                "firebaseId" to (item.firebaseId.ifEmpty { item.id }), // Like web version
                "title" to item.title,
                "name" to item.name,
                "type" to item.type,
                "posterPath" to item.posterPath,
                "posterUrl" to item.posterUrl,
                "poster_path" to item.posterUrl,
                "poster_url" to item.posterUrl,
                "backdropPath" to item.backdropPath,
                "backdropUrl" to item.backdropUrl,
                "backdrop_path" to item.backdropUrl,
                "backdrop_url" to item.backdropUrl,
                "logoUrl" to item.logoUrl,
                "logo_url" to item.logoUrl,
                "logo_path" to item.logoUrl,
                "backdropPublicityUrl" to item.backdropPublicityUrl,
                "backdrop_publicity_url" to item.backdropPublicityUrl,
                "backdropublicity_path" to item.backdropPublicityUrl,
                "overview" to item.overview,
                "vote_average" to item.vote_average,
                "release_date" to item.release_date,
                "first_air_date" to item.first_air_date,
                "addedAt" to Date(),
                "profileId" to profileId
            )
            
            android.util.Log.d("WatchlistRepository", "📍 Setting document at: users/$firestoreUserId/watchlist/${item.id}")
            android.util.Log.d("WatchlistRepository", "📄 Data to save: $data")
            
            firestore.collection("users")
                .document(firestoreUserId)
                .collection("watchlist")
                .document(item.id) // Use id as document ID like web version
                .set(data)
                .await()
            
            android.util.Log.d("WatchlistRepository", "✅ Added to watchlist: ${item.displayName}")
            Result.Success(item.id)
        } catch (e: Exception) {
            android.util.Log.e("WatchlistRepository", "❌ Error adding to watchlist", e)
            Result.Error(e)
        }
    }
    
    suspend fun removeFromWatchlist(userId: String, profileId: String, watchlistItemId: String): Result<Unit> {
        return try {
            // IMPORTANTE: Como en la versión web, profileId es en realidad el userId de Firebase
            val firestoreUserId = if (profileId.isNotEmpty() && profileId != "default") profileId else userId
            
            android.util.Log.d("WatchlistRepository", "🗑️ Removing from watchlist: $watchlistItemId")
            android.util.Log.d("WatchlistRepository", "📍 Path: users/$firestoreUserId/watchlist/$watchlistItemId")
            
            firestore.collection("users")
                .document(firestoreUserId)
                .collection("watchlist")
                .document(watchlistItemId)
                .delete()
                .await()
            
            android.util.Log.d("WatchlistRepository", "✅ Removed from watchlist: $watchlistItemId")
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("WatchlistRepository", "❌ Error removing from watchlist", e)
            Result.Error(e)
        }
    }
    
    suspend fun isInWatchlist(userId: String, profileId: String, contentId: String): Result<Boolean> {
        return try {
            // IMPORTANTE: Como en la versión web, profileId es en realidad el userId de Firebase
            val firestoreUserId = if (profileId.isNotEmpty() && profileId != "default") profileId else userId
            
            android.util.Log.d("WatchlistRepository", "🔍 Checking if item $contentId is in watchlist")
            android.util.Log.d("WatchlistRepository", "📍 Path: users/$firestoreUserId/watchlist")
            
            val existing = firestore.collection("users")
                .document(firestoreUserId)
                .collection("watchlist")
                .whereEqualTo("id", contentId)
                .get()
                .await()
            
            val result = !existing.isEmpty
            android.util.Log.d("WatchlistRepository", "✅ Item $contentId is in watchlist: $result")
            Result.Success(result)
        } catch (e: Exception) {
            android.util.Log.e("WatchlistRepository", "❌ Error checking if item is in watchlist", e)
            Result.Error(e)
        }
    }
}
