package com.anfhly.streaming.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.model.UserProfile
import com.anfhly.streaming.data.repository.ProfileRepository
import com.anfhly.streaming.data.local.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val TAG = "ProfileViewModel"

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _selectedProfile = MutableStateFlow<UserProfile?>(null)
    val selectedProfile: StateFlow<UserProfile?> = _selectedProfile.asStateFlow()

    private val _focusedProfileIndex = MutableStateFlow(0)
    val focusedProfileIndex: StateFlow<Int> = _focusedProfileIndex.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.selectedProfile.collect { profile ->
                _selectedProfile.value = profile
            }
        }
    }

    fun loadProfiles(userId: String) {
        Log.d(TAG, "loadProfiles called with userId: $userId")
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Loading
                
                when (val result = profileRepository.getUserProfiles(userId)) {
                    is Result.Success -> {
                        Log.d(TAG, "loadProfiles success: ${result.data.size} profiles")
                        _profiles.value = result.data
                        _uiState.value = if (result.data.isEmpty()) {
                            ProfileUiState.Empty
                        } else {
                            ProfileUiState.Success
                        }
                        
                        // Do NOT auto-select - let user choose manually
                        // This prevents immediate navigation crash
                    }
                    is Result.Error -> {
                        Log.e(TAG, "loadProfiles error: ${result.message}")
                        val errorMessage = result.message ?: "Unknown error"
                        // Check if this is a "not found" error that should be treated as empty
                        if (errorMessage.contains("not found") || errorMessage.contains("Profiles array not found")) {
                            Log.w(TAG, "Treating error as empty state - user needs to create profiles")
                            _profiles.value = emptyList()
                            _uiState.value = ProfileUiState.Empty
                        } else {
                            _uiState.value = ProfileUiState.Error(errorMessage)
                        }
                    }
                    is Result.Loading -> {
                        _uiState.value = ProfileUiState.Loading
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in loadProfiles: ${e.message}", e)
                _uiState.value = ProfileUiState.Error("Error inesperado: ${e.message}")
            }
        }
    }

    fun createProfile(userId: String, name: String, isKids: Boolean = false, avatar: String = "adult1", customImageUrl: String? = null) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            
            val profile = UserProfile(
                userId = userId,
                name = name,
                avatar = if (customImageUrl != null) "custom" else avatar,
                customImageUrl = customImageUrl,
                isKids = isKids,
                isLocked = false,
                profileColor = if (isKids) "#fbbf24" else "#3b82f6"
            )
            
            when (val result = profileRepository.createProfile(userId, profile)) {
                is Result.Success -> {
                    loadProfiles(userId)
                }
                is Result.Error -> {
                    _uiState.value = ProfileUiState.Error(result.message)
                }
                is Result.Loading -> {
                    _uiState.value = ProfileUiState.Loading
                }
            }
        }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            
            when (val result = profileRepository.updateProfile(profile.id, profile)) {
                is Result.Success -> {
                    // If this is the currently selected profile, update it in DataStore too
                    if (_selectedProfile.value?.id == profile.id) {
                        Log.d(TAG, "Updating selected profile in DataStore: ${profile.name}, customImageUrl=${profile.customImageUrl}")
                        selectProfile(profile)
                    }
                    loadProfiles(profile.userId)
                }
                is Result.Error -> {
                    _uiState.value = ProfileUiState.Error(result.message)
                }
                is Result.Loading -> {
                    _uiState.value = ProfileUiState.Loading
                }
            }
        }
    }

    fun deleteProfile(profileId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            
            when (val result = profileRepository.deleteProfile(profileId, userId)) {
                is Result.Success -> {
                    // Clear selected profile if it was deleted
                    if (_selectedProfile.value?.id == profileId) {
                        selectProfile(null)
                    }
                    loadProfiles(userId)
                }
                is Result.Error -> {
                    _uiState.value = ProfileUiState.Error(result.message)
                }
                is Result.Loading -> {
                    _uiState.value = ProfileUiState.Loading
                }
            }
        }
    }

    fun selectProfile(profile: UserProfile?) {
        viewModelScope.launch {
            _selectedProfile.value = profile
            settingsDataStore.saveSelectedProfile(profile)
        }
    }

    fun verifyPin(profile: UserProfile, pin: String): Boolean {
        return profileRepository.verifyPin(profile, pin)
    }

    fun navigateToNextProfile() {
        if (_profiles.value.isEmpty()) return
        val currentIndex = _focusedProfileIndex.value
        if (currentIndex < _profiles.value.size - 1) {
            _focusedProfileIndex.value = currentIndex + 1
        }
        // No hacer ciclo - quedarse en el último perfil
    }

    fun navigateToPreviousProfile() {
        if (_profiles.value.isEmpty()) return
        val currentIndex = _focusedProfileIndex.value
        if (currentIndex > 0) {
            _focusedProfileIndex.value = currentIndex - 1
        }
        // No hacer ciclo - quedarse en el primer perfil
    }

    fun setFocusedProfileIndex(index: Int) {
        if (_profiles.value.isEmpty()) {
            _focusedProfileIndex.value = 0
            return
        }
        _focusedProfileIndex.value = index.coerceIn(0, _profiles.value.size - 1)
    }

    fun clearError() {
        if (_uiState.value is ProfileUiState.Error) {
            _uiState.value = ProfileUiState.Success
        }
    }

    sealed class ProfileUiState {
        data object Loading : ProfileUiState()
        data object Success : ProfileUiState()
        data object Empty : ProfileUiState()
        data class Error(val message: String) : ProfileUiState()
    }
}
