package com.anfhly.streaming.data.repository

import android.util.Log
import com.anfhly.streaming.data.model.UserProfile
import com.anfhly.streaming.data.model.Result
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.util.Date

class ProfileRepository {
    
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "ProfileRepository"
    
    /**
     * Get all profiles for a user from the profiles array in the user document
     */
    suspend fun getUserProfiles(userId: String): Result<List<UserProfile>> {
        Log.d(TAG, "Loading profiles from user document for: $userId")
        return try {
            // Read the user document directly
            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()
            
            if (!userDoc.exists()) {
                Log.w(TAG, "User document not found for: $userId")
                return Result.Error(Exception("User document not found"))
            }
            
            Log.d(TAG, "User document found: ${userDoc.id}")
            Log.d(TAG, "Document data: ${userDoc.data}")
            
            // Get the profiles array from the user document
            val profilesArray = userDoc.get("profiles") as? List<Map<String, Any>>
            
            if (profilesArray == null) {
                Log.w(TAG, "No profiles array found in user document. Available fields: ${userDoc.data?.keys}")
                return Result.Error(Exception("Profiles array not found in user document"))
            }
            
            Log.d(TAG, "Found ${profilesArray.size} profiles in array")
            
            if (profilesArray.isEmpty()) {
                Log.d(TAG, "Profiles array is empty - user needs to create profiles")
                return Result.Success(emptyList())
            }
            
            val profiles = profilesArray.mapIndexed { index, profileData ->
                Log.d(TAG, "Processing profile $index: $profileData")
                parseProfileFromMap(profileData, userId)
            }
            
            Log.d(TAG, "Successfully loaded ${profiles.size} profiles")
            Result.Success(profiles)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profiles: ${e.message}", e)
            Result.Error(e)
        }
    }
    
    /**
     * Parse profile data from Firestore map with error handling
     */
    private fun parseProfileFromMap(data: Map<String, Any>, userId: String): UserProfile {
        return try {
            // Handle timestamp fields which can be either Timestamp objects or Maps with seconds/nanoseconds
            val createdAt = parseTimestamp(data["createdAt"])
            val updatedAt = parseTimestamp(data["updatedAt"])
            
            UserProfile(
                id = data["id"] as? String ?: "",
                userId = data["userId"] as? String ?: userId,
                name = data["name"] as? String ?: "",
                avatar = data["avatar"] as? String ?: "adult1",
                customImageUrl = data["customImageUrl"] as? String,
                isKids = data["isKids"] as? Boolean ?: false,
                isLocked = data["isLocked"] as? Boolean ?: false,
                pin = data["pin"] as? String,
                profileColor = data["profileColor"] as? String ?: "#3b82f6",
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing profile data: $data", e)
            // Return a default profile to prevent crash
            UserProfile(
                id = data["id"] as? String ?: "unknown",
                userId = userId,
                name = data["name"] as? String ?: "Error",
                avatar = "adult1",
                customImageUrl = null,
                isKids = false,
                isLocked = false,
                pin = null,
                profileColor = "#3b82f6",
                createdAt = null,
                updatedAt = null
            )
        }
    }
    
    /**
     * Parse timestamp from Firestore - handles both Timestamp objects and Map representations
     */
    private fun parseTimestamp(timestamp: Any?): Date? {
        return when (timestamp) {
            is Timestamp -> timestamp.toDate()
            is Date -> timestamp
            is Map<*, *> -> {
                // Handle map format: {seconds: 123, nanoseconds: 456}
                val seconds = (timestamp["seconds"] as? Number)?.toLong()
                val nanoseconds = (timestamp["nanoseconds"] as? Number)?.toInt() ?: 0
                if (seconds != null) {
                    Date(seconds * 1000 + nanoseconds / 1000000)
                } else null
            }
            else -> null
        }
    }
    
    /**
     * Create a new profile - adds to the profiles array in user document
     */
    suspend fun createProfile(userId: String, profile: UserProfile): Result<String> {
        Log.d(TAG, "Creating profile for user: $userId, name: ${profile.name}")
        return try {
            // Check profile limit
            val existingProfiles = getUserProfiles(userId)
            if (existingProfiles is Result.Success && existingProfiles.data.size >= 5) {
                Log.w(TAG, "Profile limit reached for user: $userId")
                return Result.Error(Exception("Has alcanzado el límite máximo de 5 perfiles"))
            }
            
            // Check if user document exists
            val userDoc = firestore.collection("users").document(userId).get().await()
            if (!userDoc.exists()) {
                Log.w(TAG, "User document not found for: $userId, creating with empty profiles array")
                // Create the user document with empty profiles array
                val initialData = hashMapOf(
                    "userId" to userId,
                    "profiles" to emptyList<Map<String, Any>>(),
                    "profilesCount" to 0,
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
                firestore.collection("users").document(userId).set(initialData).await()
                Log.d(TAG, "Created user document for: $userId")
            }
            
            val now = Timestamp.now()
            val profileId = "${userId}_${System.currentTimeMillis()}"
            
            val profileData = hashMapOf(
                "id" to profileId,
                "userId" to userId,
                "name" to profile.name,
                "avatar" to profile.avatar,
                "customImageUrl" to (profile.customImageUrl ?: ""),
                "isKids" to profile.isKids,
                "isLocked" to profile.isLocked,
                "pin" to (profile.pin ?: ""),
                "profileColor" to profile.profileColor,
                "createdAt" to now,
                "updatedAt" to now
            )
            
            Log.d(TAG, "Profile data prepared: $profileData")
            
            // Add to profiles array using arrayUnion
            firestore.collection("users")
                .document(userId)
                .update("profiles", FieldValue.arrayUnion(profileData))
                .await()
            
            Log.d(TAG, "Profile added to array successfully")
            
            // Increment profilesCount
            firestore.collection("users")
                .document(userId)
                .update("profilesCount", FieldValue.increment(1))
                .await()
            
            Log.d(TAG, "Profile created successfully: $profileId")
            Result.Success(profileId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating profile: ${e.message}", e)
            Result.Error(e)
        }
    }
    
    /**
     * Update an existing profile in the array
     */
    suspend fun updateProfile(profileId: String, profile: UserProfile): Result<Unit> {
        return try {
            Log.d(TAG, "Updating profile: id=$profileId, name=${profile.name}, avatar=${profile.avatar}, customImageUrl=${profile.customImageUrl}")
            
            // For array updates, we need to:
            // 1. Read the current profiles array
            // 2. Find and update the specific profile
            // 3. Write the entire array back
            
            val userDoc = firestore.collection("users")
                .document(profile.userId)
                .get()
                .await()
            
            val profilesArray = userDoc.get("profiles") as? MutableList<Map<String, Any>>
                ?: return Result.Error(Exception("Profiles array not found"))
            
            Log.d(TAG, "Found profiles array with ${profilesArray.size} profiles")
            
            // Find the profile index
            val index = profilesArray.indexOfFirst { it["id"] == profileId }
            if (index == -1) {
                Log.e(TAG, "Profile not found in array: $profileId")
                return Result.Error(Exception("Profile not found"))
            }
            
            Log.d(TAG, "Found profile at index $index")
            
            // Update the profile
            val updatedProfile = hashMapOf(
                "id" to profileId,
                "userId" to profile.userId,
                "name" to profile.name,
                "avatar" to profile.avatar,
                "customImageUrl" to (profile.customImageUrl ?: ""),
                "isKids" to profile.isKids,
                "isLocked" to profile.isLocked,
                "pin" to (profile.pin ?: ""),
                "profileColor" to profile.profileColor,
                "createdAt" to (profilesArray[index]["createdAt"] ?: Timestamp.now()),
                "updatedAt" to Timestamp.now()
            )
            
            Log.d(TAG, "Updated profile data: $updatedProfile")
            
            profilesArray[index] = updatedProfile
            
            // Write back the entire array
            firestore.collection("users")
                .document(profile.userId)
                .update("profiles", profilesArray)
                .await()
            
            Log.d(TAG, "Profile updated successfully in Firestore")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile: ${e.message}", e)
            Result.Error(e)
        }
    }
    
    /**
     * Delete a profile from the array
     */
    suspend fun deleteProfile(profileId: String, userId: String): Result<Unit> {
        return try {
            // Read current profiles
            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()
            
            val profilesArray = userDoc.get("profiles") as? MutableList<Map<String, Any>>
                ?: return Result.Error(Exception("Profiles array not found"))
            
            // Remove the profile
            val profileToRemove = profilesArray.find { it["id"] == profileId }
                ?: return Result.Error(Exception("Profile not found"))
            
            // Use arrayRemove to delete
            firestore.collection("users")
                .document(userId)
                .update("profiles", FieldValue.arrayRemove(profileToRemove))
                .await()
            
            // Decrement profilesCount
            firestore.collection("users")
                .document(userId)
                .update("profilesCount", FieldValue.increment(-1))
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    /**
     * Delete all profiles for a user
     */
    suspend fun deleteAllProfiles(userId: String): Result<Unit> {
        return try {
            firestore.collection("users")
                .document(userId)
                .update(
                    "profiles", emptyList<Map<String, Any>>(),
                    "profilesCount", 0
                )
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    /**
     * Verify PIN for a locked profile
     */
    fun verifyPin(profile: UserProfile, pin: String): Boolean {
        return profile.pin == pin
    }
}
