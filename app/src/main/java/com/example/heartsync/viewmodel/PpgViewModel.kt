// app/src/main/java/com/example/HeartSync/viewmodel/PpgViewModel.kt
package com.example.heartsync.viewmodel

import android.util.Log
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
import java.util.Collections.list
import java.util.UUID

class PpgViewModel(
    private val repo: PpgRepository
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val _userId : String?
        get()  = auth.currentUser?.uid

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
            val uid = _userId ?: run{
                _events.value = emptyList()
                return@launch
            }

            repo.observeRecent(uid, _sessionId.value, limit = 200L)
                .collectLatest{ list->
                    _events.value = list
                }
        }
    }

    fun currentUserId(): String =
        _userId ?: throw IllegalStateException("User must be logged in")

    fun saveEvent(ev: PpgEvent) {
        val uid = _userId ?: return  // 로그인 안 됐으면 아무 것도 안 함
        viewModelScope.launch {
            runCatching {
                repo.uploadRecord(uid, _sessionId.value, ev)
            }.onFailure { e ->
                Log.e("PpgViewModel", "upload failed", e)
            }
        }
    }

    private fun newSessionId(): String {
        val iso = OffsetDateTime.now(ZoneOffset.UTC).toString().replace(":", "-")
        val suffix = UUID.randomUUID().toString().take(8)
        return "${iso}_$suffix"
    }
}
