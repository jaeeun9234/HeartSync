// app/src/main/java/com/example/heartsync/viewmodel/BleViewModel.kt
package com.example.heartsync.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.data.model.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleViewModel(app: Application) : AndroidViewModel(app) {

    // UI에서 볼 로그(간단 문자열 버퍼)
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private fun appendLog(line: String) {
        val cur = _logs.value.toMutableList()
        if (cur.size > 200) cur.removeAt(0)   // API 24 안전
        cur.add(line)
        _logs.value = cur
    }

    // 앱 전체에서 단 하나만 쓰일 BLE 클라이언트
    private val client = PpgBleClient(
        ctx = app.applicationContext,
        onLine = { s -> appendLog("<< $s") },
        onError = { e -> appendLog("!! $e") },
        filterByService = false
    )

    // 화면들이 구독할 상태
    val scanning = client.scanning
    val scanResults = client.scanResults
    val connectionState = client.connectionState

    // 액션들
    fun startScan() = client.startScan()
    fun stopScan() = client.stopScan()
    fun connect(device: BleDevice) = client.connect(device)
    fun disconnect() = client.disconnect()
    fun writeCmd(text: String) = client.writeCmd(text)

    override fun onCleared() {
        super.onCleared()
        client.disconnect()  // 액티비티 종료 시 정리
    }
}
