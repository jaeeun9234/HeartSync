// app/src/main/java/com/example/heartsync/viewmodel/BleViewModel.kt
package com.example.heartsync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.data.model.BleDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BleViewModel(app: Application) : AndroidViewModel(app) {

    private val client = PpgBleClient(app)

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults.asStateFlow()

    private val _connectionState =
        MutableStateFlow<PpgBleClient.ConnectionState>(PpgBleClient.ConnectionState.Disconnected)
    val connectionState: StateFlow<PpgBleClient.ConnectionState> = _connectionState.asStateFlow()

    private var scanJob: Job? = null
    private var connJob: Job? = null

    fun startScan() {
        if (_scanning.value) return
        _scanning.value = true

        // 실제 스캔 시작
        client.startScan()

        // 기존 수집 중지 후 재시작
        scanJob?.cancel()
        scanJob = client.scanResults
            .onEach { list -> _scanResults.value = list }
            .launchIn(viewModelScope)
    }

    fun stopScan() {
        _scanning.value = false
        scanJob?.cancel()
        client.stopScan()
    }

    fun connect(device: BleDevice) {
        // 스캔 중이면 중지
        stopScan()

        // 연결 요청
        client.connect(device)

        // 연결 상태 수집 (중복 수집 방지)
        connJob?.cancel()
        connJob = client.connectionState
            .onEach { state -> _connectionState.value = state }
            .launchIn(viewModelScope)
    }

    fun disconnect() {
        client.disconnect()
        _connectionState.value = PpgBleClient.ConnectionState.Disconnected
        // 필요시 수집 중지도 함께
        connJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        connJob?.cancel()
        client.stopScan()
        client.disconnect()
    }
}
