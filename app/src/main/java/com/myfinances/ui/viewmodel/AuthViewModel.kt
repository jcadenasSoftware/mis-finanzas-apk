package com.jcadenas.xpendz.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.jcadenas.xpendz.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val isLoggedIn: Boolean = false,
    val user: FirebaseUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState(
        isLoggedIn = authRepository.isLoggedIn,
        user = authRepository.currentUser
    ))
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                _authState.value = _authState.value.copy(
                    isLoggedIn = user != null,
                    user = user
                )
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val result = authRepository.signInWithGoogle(idToken)
            result.fold(
                onSuccess = { user ->
                    Log.d("AuthViewModel", "Google sign-in successful: ${user?.email}")
                    _authState.value = _authState.value.copy(
                        isLoggedIn = true,
                        user = user,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    Log.e("AuthViewModel", "Google sign-in failed", e)
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Error al iniciar sesión"
                    )
                }
            )
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val result = authRepository.signInWithEmail(email, password)
            result.fold(
                onSuccess = { user ->
                    _authState.value = _authState.value.copy(
                        isLoggedIn = true,
                        user = user,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Error al iniciar sesión"
                    )
                }
            )
        }
    }

    fun createAccount(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val result = authRepository.createUserWithEmail(email, password)
            result.fold(
                onSuccess = { user ->
                    _authState.value = _authState.value.copy(
                        isLoggedIn = true,
                        user = user,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Error al crear cuenta"
                    )
                }
            )
        }
    }

    fun sendPasswordReset(email: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val result = authRepository.sendPasswordResetEmail(email)
            result.fold(
                onSuccess = {
                    _authState.value = _authState.value.copy(isLoading = false)
                    onSuccess()
                },
                onFailure = { e ->
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = e.message ?: "No se pudo enviar el correo de recuperación"
                    )
                }
            )
        }
    }

    fun signOut() {
        authRepository.signOut()
        _authState.value = AuthState()
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }
}
