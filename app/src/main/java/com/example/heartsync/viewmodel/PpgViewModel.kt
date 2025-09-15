// app/src/main/java/com/example/HeartSync/viewmodel/PpgViewModel.kt
package com.example.heartsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.heartsync.data.model.PpgEvent
import com.example.heartsync.data.remote.PpgRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PpgViewModel(
    private val repo: PpgRepository
) : ViewModel() {

    private val _userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

    private val _sessionId = MutableStateFlow(newSessionId())
    val sessionId: StateFlow<String> = _sessionId

    private val _events = MutableStateFlow<List<PpgEvent>>(emptyList())
    val events: StateFlow<List<PpgEvent>> = _events

    fun startNewSession() {
        _sessionId.value = newSessionId()
        // 구독 재시작
        observeCurrentSession()
    }

    fun observeCurrentSession() {
        viewModelScope.launch {
            repo.observeRecent(_userId, _sessionId.value, limit = 200)
                .collectLatest { _events.value = it }
        }
    }

    fun currentUserId(): String = _userId

    private fun newSessionId(): String {
        val iso = OffsetDateTime.now(ZoneOffset.UTC).toString().replace(":", "-")
        val suffix = UUID.randomUUID().toString().take(8)
        return "${iso}_$suffix"
    }
}
