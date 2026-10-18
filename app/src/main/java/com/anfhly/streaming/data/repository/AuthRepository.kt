package com.anfhly.streaming.data.repository

import android.util.Log
import com.anfhly.streaming.data.model.AuthState
import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.model.User
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class AuthRepository {
    
    private val auth: FirebaseAuth = Firebase.auth
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "AuthRepository"
    
    /**
     * Observable auth state
     */
    val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            val state = if (user != null) {
                AuthState.Authenticated(mapFirebaseUserToUser(user))
            } else {
                AuthState.Unauthenticated
            }
            trySend(state)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
    
    /**
     * Current user
     */
    val currentUser: User?
        get() = auth.currentUser?.let { mapFirebaseUserToUser(it) }
    
    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean = auth.currentUser != null
    
    /**
     * Sign in with email and password
     */
    suspend fun signIn(email: String, password: String): Result<User> {
        Log.d(TAG, "Attempting sign in for: $email")
        return try {
            val result: AuthResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Log.d(TAG, "Sign in successful for user: ${user.uid}")
                // Update last login
                updateLastLogin(user.uid)
                
                // Verify Firestore user document exists
                verifyUserDocument(user.uid)
                
                Result.Success(mapFirebaseUserToUser(user))
            } else {
                Log.e(TAG, "Sign in failed: user is null")
                Result.Error(Exception("Authentication failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign in error: ${e.message}", e)
            Result.Error(e, getErrorMessage(e))
        }
    }
    
    /**
     * Register with email and password
     */
    suspend fun register(email: String, password: String, displayName: String): Result<User> {
        Log.d(TAG, "Attempting registration for: $email")
        return try {
            val result: AuthResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Log.d(TAG, "Registration successful for user: ${user.uid}")
                // Update profile
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profileUpdates).await()
                
                // Create user document in Firestore
                createUserDocument(user.uid, email, displayName)
                
                Result.Success(mapFirebaseUserToUser(user))
            } else {
                Log.e(TAG, "Registration failed: user is null")
                Result.Error(Exception("Registration failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration error: ${e.message}", e)
            Result.Error(e, getErrorMessage(e))
        }
    }
    
    /**
     * Sign out
     */
    fun signOut() {
        auth.signOut()
    }
    
    /**
     * Send password reset email
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, getErrorMessage(e))
        }
    }
    
    /**
     * Delete user account
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser
            if (user != null) {
                // Delete user document from Firestore
                firestore.collection("users").document(user.uid).delete().await()
                // Delete user from Auth
                user.delete().await()
                Result.Success(Unit)
            } else {
                Result.Error(Exception("No user logged in"))
            }
        } catch (e: Exception) {
            Result.Error(e, getErrorMessage(e))
        }
    }
    
    /**
     * Get user status from Firestore
     */
    suspend fun getUserStatus(userId: String): Result<Map<String, Any>> {
        Log.d(TAG, "Getting user status from Firestore for: $userId")
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            val data = doc.data ?: emptyMap()
            Log.d(TAG, "User document data: $data")
            Result.Success(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user status: ${e.message}", e)
            Result.Error(e, getErrorMessage(e))
        }
    }
    
    private suspend fun createUserDocument(uid: String, email: String, displayName: String) {
        Log.d(TAG, "Creating user document in Firestore for: $uid")
        val userData = hashMapOf(
            "uid" to uid,
            "email" to email,
            "displayName" to displayName,
            "createdAt" to Date(),
            "role" to "user",
            "isPremium" to false,
            "canUpload" to false,
            "canViewContent" to true,
            "shouldShowAds" to true,
            "profiles" to emptyList<Map<String, Any>>(),
            "profilesCount" to 0,
            "updatedAt" to Date()
        )
        try {
            firestore.collection("users").document(uid).set(userData).await()
            Log.d(TAG, "User document created successfully with profiles array")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user document: ${e.message}", e)
        }
    }
    
    private suspend fun updateLastLogin(uid: String) {
        try {
            Log.d(TAG, "Updating last login for user: $uid")
            firestore.collection("users").document(uid)
                .update("lastLoginAt", Date())
                .await()
            Log.d(TAG, "Last login updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last login: ${e.message}")
        }
    }
    
    private suspend fun verifyUserDocument(uid: String) {
        try {
            Log.d(TAG, "Verifying user document exists for: $uid")
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                Log.d(TAG, "User document found in Firestore")
                // Check if profiles array exists, if not initialize it
                if (!doc.contains("profiles")) {
                    Log.w(TAG, "User document missing profiles array - initializing")
                    firestore.collection("users").document(uid)
                        .update(
                            "profiles", emptyList<Map<String, Any>>(),
                            "profilesCount", 0,
                            "updatedAt", Date()
                        )
                        .await()
                    Log.d(TAG, "Profiles array initialized for existing user")
                }
            } else {
                Log.w(TAG, "User document NOT found in Firestore - creating default document")
                // Create default user document if it doesn't exist
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    createUserDocument(uid, currentUser.email ?: "", currentUser.displayName ?: "Usuario")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying user document: ${e.message}", e)
        }
    }
    
    private fun mapFirebaseUserToUser(firebaseUser: FirebaseUser): User {
        return User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = firebaseUser.displayName,
            photoUrl = firebaseUser.photoUrl?.toString(),
            emailVerified = firebaseUser.isEmailVerified
        )
    }
    
    private fun getErrorMessage(e: Exception): String {
        return when (e.message) {
            "There is no user record corresponding to this identifier. The user may have been deleted." ->
                "No existe una cuenta con este email"
            "The password is invalid or the user does not have a password." ->
                "Contraseña incorrecta"
            "The email address is badly formatted." ->
                "Email inválido"
            "We have blocked all requests from this device due to unusual activity. Try again later." ->
                "Demasiados intentos. Intenta más tarde"
            "The supplied auth credential is incorrect, malformed or has expired." ->
                "Credenciales inválidas. Verifica tu email y contraseña"
            "The email address is already in use by another account." ->
                "Este email ya está registrado"
            "Password should be at least 6 characters" ->
                "La contraseña debe tener al menos 6 caracteres"
            else -> e.message ?: "Error desconocido"
        }
    }
}
