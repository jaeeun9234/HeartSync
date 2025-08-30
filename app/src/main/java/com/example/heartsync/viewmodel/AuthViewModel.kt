package com.example.heartsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.heartsync.data.remote.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AuthEvent {
    data object LoggedIn : AuthEvent
    data object LoggedOut : AuthEvent
    data class Error(val msg: String) : AuthEvent
    data class IdCheckResult(val id: String, val available: Boolean) : AuthEvent
}

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _isLoggedIn = MutableStateFlow(repo.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    val events = Channel<AuthEvent>(Channel.BUFFERED)

    private val listener = FirebaseAuth.AuthStateListener {
        _isLoggedIn.value = (it.currentUser != null)
        viewModelScope.launch {
            if (it.currentUser != null) events.send(AuthEvent.LoggedIn) else events.send(AuthEvent.LoggedOut)
        }
    }

    init { FirebaseAuth.getInstance().addAuthStateListener(listener) }
    override fun onCleared() {
        FirebaseAuth.getInstance().removeAuthStateListener(listener)
        super.onCleared()
    }

    fun loginWithId(id: String, pw: String) = viewModelScope.launch {
        runCatching { repo.loginWithId(id, pw) }
            .onFailure { events.send(AuthEvent.Error(it.message ?: "로그인 실패")) }
    }

    fun checkIdAvailability(id: String) = viewModelScope.launch {
        runCatching { repo.isIdAvailable(id) }
            .onSuccess { available -> events.send(AuthEvent.IdCheckResult(id, available)) }
            .onFailure { events.send(AuthEvent.Error(it.message ?: "ID 확인 실패")) }
    }

    fun register(id:String, name:String, phone:String, birth:String, email:String, pw:String) =
        viewModelScope.launch {
            runCatching { repo.register(id, name, phone, birth, email, pw) }
                .onFailure { events.send(AuthEvent.Error(it.message ?: "회원가입 실패")) }
        }

    fun logout() = repo.logout()
}
