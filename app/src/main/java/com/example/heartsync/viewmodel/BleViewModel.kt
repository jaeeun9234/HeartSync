// app/src/main/java/com/example/heartsync/viewmodel/BleViewModel.kt
package com.example.heartsync.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.data.model.BleDevice
import com.example.heartsync.data.model.GraphState
import com.example.heartsync.data.remote.PpgRepository
import com.example.heartsync.service.MeasureService
import kotlinx.coroutines.flow.update
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

    private val MAX_GRAPH_POINTS = 600

    private val _graphState = MutableStateFlow(GraphState())
    val graphState: StateFlow<GraphState> = _graphState.asStateFlow()

    init {
        // PpgRepository의 실시간 스무딩 샘플 수집 → 최근 N포인트 버퍼 유지
        PpgRepository.smoothedFlow
            .onEach { (l, r) ->
                _graphState.update { prev ->
                    val newL = (prev.smoothedL + l.toFloat()).takeLast(MAX_GRAPH_POINTS)
                    val newR = (prev.smoothedR + r.toFloat()).takeLast(MAX_GRAPH_POINTS)
                    prev.copy(smoothedL = newL, smoothedR = newR)
                }
            }
            .launchIn(viewModelScope)
    }

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
        clearGraph()
    }

    fun clearGraph() {
        _graphState.value = GraphState()
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        connJob?.cancel()
        client.stopScan()
        client.disconnect()
    }

    /** 연결된 기기로 측정(MeasureService) 시작 */
    fun startMeasure(device: BleDevice) {
        val ctx = getApplication<Application>()
        val it = Intent(ctx, MeasureService::class.java).apply {
            putExtra(MeasureService.EXTRA_DEVICE_NAME, device.name)
            putExtra(MeasureService.EXTRA_DEVICE_ADDR, device.address)
        }
        // API 26+ 대응
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(it)
        } else {
            ctx.startService(it)
        }
    }

    /** 측정 중지 */
    fun stopMeasure() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, MeasureService::class.java))
    }
}
