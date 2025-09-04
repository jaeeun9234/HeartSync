package com.example.heartsync.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.heartsync.data.model.BleDevice
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.ble.PpgBleClient.ConnectionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BleUiState(
    val scanning: Boolean = false,
    val devices: List<BleDevice> = emptyList(),
    val connection: ConnectionState = ConnectionState.Disconnected
) {
    val isConnected: Boolean get() = connection is ConnectionState.Connected
    val connectedName: String? get() =
        (connection as? ConnectionState.Connected)?.device?.name
            ?: (connection as? ConnectionState.Connected)?.device?.address
}

class BleViewModel(
    private val client: PpgBleClient
) : ViewModel() {

    private val _state = MutableStateFlow(BleUiState())
    val state: StateFlow<BleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { client.scanning.collect { s -> _state.update { it.copy(scanning = s) } } }
        viewModelScope.launch { client.scanResults.collect { list -> _state.update { it.copy(devices = list) } } }
        viewModelScope.launch { client.connectionState.collect { cs -> _state.update { it.copy(connection = cs) } } }
    }

    fun startScan() = client.startScan()
    fun stopScan() = client.stopScan()
    fun connect(d: BleDevice) = client.connect(d)
    fun disconnect() = client.disconnect()

    companion object {
        fun provideFactory(appCtx: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BleViewModel(PpgBleClient(appCtx) { /* UI에선 라인 무시 */ }) as T
            }
        }
    }

}
