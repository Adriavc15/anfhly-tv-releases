package com.anfhly.streaming.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anfhly.streaming.data.model.AuthState
import com.anfhly.streaming.data.model.Result
import com.anfhly.streaming.data.model.User
import com.anfhly.streaming.data.repository.AuthRepository
import com.anfhly.streaming.data.local.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow().apply {
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                _authState.value = state
            }
        }
    }

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _rememberEmail = MutableStateFlow(false)
    val rememberEmail: StateFlow<Boolean> = _rememberEmail.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.rememberedEmail.collect { email ->
                _rememberEmail.value = email != null
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            
            when (val result = authRepository.signIn(email, password)) {
                is Result.Success -> {
                    if (_rememberEmail.value) {
                        settingsDataStore.saveRememberedEmail(email)
                    } else {
                        settingsDataStore.saveRememberedEmail(null)
                    }
                    _loginState.value = LoginUiState.Success(result.data)
                }
                is Result.Error -> {
                    _loginState.value = LoginUiState.Error(result.message)
                }
                is Result.Loading -> {
                    _loginState.value = LoginUiState.Loading
                }
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            
            when (val result = authRepository.register(email, password, displayName)) {
                is Result.Success -> {
                    _loginState.value = LoginUiState.Success(result.data)
                }
                is Result.Error -> {
                    _loginState.value = LoginUiState.Error(result.message)
                }
                is Result.Loading -> {
                    _loginState.value = LoginUiState.Loading
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            settingsDataStore.saveSelectedProfile(null)
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            
            when (val result = authRepository.sendPasswordReset(email)) {
                is Result.Success -> {
                    _loginState.value = LoginUiState.PasswordResetSent
                }
                is Result.Error -> {
                    _loginState.value = LoginUiState.Error(result.message)
                }
                is Result.Loading -> {
                    _loginState.value = LoginUiState.Loading
                }
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            
            when (val result = authRepository.deleteAccount()) {
                is Result.Success -> {
                    _loginState.value = LoginUiState.AccountDeleted
                }
                is Result.Error -> {
                    _loginState.value = LoginUiState.Error(result.message)
                }
                is Result.Loading -> {
                    _loginState.value = LoginUiState.Loading
                }
            }
        }
    }

    fun setRememberEmail(remember: Boolean) {
        _rememberEmail.value = remember
    }

    fun resetLoginState() {
        _loginState.value = LoginUiState.Idle
    }

    sealed class LoginUiState {
        data object Idle : LoginUiState()
        data object Loading : LoginUiState()
        data class Success(val user: User) : LoginUiState()
        data class Error(val message: String) : LoginUiState()
        data object PasswordResetSent : LoginUiState()
        data object AccountDeleted : LoginUiState()
    }
}
