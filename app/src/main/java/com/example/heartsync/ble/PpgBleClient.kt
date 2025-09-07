// app/src/main/java/com/example/heartsync/ble/PpgBleClient.kt
package com.example.heartsync.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.heartsync.data.model.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * HeartSync BLE 클라이언트 (UI와만 맞춘 미니멀 버전)
 */
class PpgBleClient(
    private val ctx: Context,
    private val onLine: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val filterByService: Boolean = false
) {
    // ===== UUID (교체 필요) =====
    private val serviceUuid: UUID = UUID.fromString("12345678-0000-1000-8000-00805f9b34fb")
    private val charCmdUuid: UUID = UUID.fromString("0000AAAC-0000-1000-8000-00805f9b34fb") // (선택) Write
    private val notifyCharUuids: List<UUID> = listOf(
        UUID.fromString("0000ABCD-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb"),
    )
    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ===== UI State =====
    sealed interface ConnectionState {
        data object Disconnected : ConnectionState
        data object Connecting : ConnectionState
        data class Connected(val device: BleDevice) : ConnectionState
        data class Failed(val reason: String) : ConnectionState
    }

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults.asStateFlow()

    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ===== BLE handles =====
    private val btManager get() =
        ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter: BluetoothAdapter? get() = btManager.adapter
    private val scanner: BluetoothLeScanner? get() = btAdapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var cmdChar: BluetoothGattCharacteristic? = null
    private val descriptorQueue = LinkedBlockingQueue<BluetoothGattDescriptor>()
    private var scanCallback: ScanCallback? = null

    // ===== Permission helpers =====
    private fun hasScanPerm(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

    private fun hasConnectPerm(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

    private fun hasLegacyLocation(): Boolean =
        Build.VERSION.SDK_INT < 31 &&
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    // ===== Scan =====
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (btAdapter?.isEnabled != true) { onError("블루투스가 꺼져 있습니다."); return }
        if (!(hasScanPerm() || hasLegacyLocation())) { onError("스캔 권한이 없습니다."); return }
        if (_scanning.value) return

        _scanResults.value = emptyList()
        _scanning.value = true

        val filters = if (filterByService)
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build())
        else null

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val item = BleDevice(dev.name, dev.address)
                val cur = _scanResults.value
                if (cur.none { it.address == item.address }) {
                    _scanResults.value = cur + item
                }
            }
            override fun onScanFailed(errorCode: Int) {
                _scanning.value = false
                onError("스캔 실패: $errorCode")
            }
        }
        scanner?.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        _scanning.value = false
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
    }

    // ===== Connect / Disconnect =====
    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        if (!hasConnectPerm()) { onError("연결 권한이 없습니다."); return }
        stopScan()
        _connectionState.value = ConnectionState.Connecting

        val btDev = try {
            BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            _connectionState.value = ConnectionState.Failed("잘못된 MAC 주소"); return
        }
        gatt = btDev.connectGatt(ctx, /*autoConnect*/ false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            if (hasConnectPerm()) {
                gatt?.disconnect()
                gatt?.close()
            }
        } catch (_: SecurityException) { /* no-op */ }
        finally {
            gatt = null
            cmdChar = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    // ===== Write (선택) =====
    @SuppressLint("MissingPermission")
    fun writeCmd(text: String) {
        val c = cmdChar ?: return
        if (!hasConnectPerm()) return
        val value = text.toByteArray()
        if (Build.VERSION.SDK_INT >= 33) {
            gatt?.writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            run { c.value = value; gatt?.writeCharacteristic(c) }
        }
    }

    // ===== GATT Callback =====
    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Failed("GATT 오류: $status"); return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!hasConnectPerm()) return
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Failed("서비스 검색 실패: $status"); return
            }
            val svc = gatt.getService(this@PpgBleClient.serviceUuid)
            if (svc == null) { _connectionState.value = ConnectionState.Failed("서비스 UUID 미일치"); return }

            // (선택) 커맨드 특성 잡기
            cmdChar = svc.getCharacteristic(this@PpgBleClient.charCmdUuid)

            // Notify 특성들 전부 CCCD enable (큐로 순차 처리) — forEach 대신 for 루프
            for (uuid in this@PpgBleClient.notifyCharUuids) {
                val ch = svc.getCharacteristic(uuid) ?: continue
                gatt.setCharacteristicNotification(ch, true)
                val cccd = ch.getDescriptor(this@PpgBleClient.cccdUuid) ?: continue
                // API 별 descriptor write 처리: 값 세팅은 여기서
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                descriptorQueue.add(cccd)
            }
            writeNextDescriptor()

            val dev = BleDevice(gatt.device?.name, gatt.device?.address ?: "Unknown")
            _connectionState.value = ConnectionState.Connected(dev)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            writeNextDescriptor()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value ?: return
            val line = bytes.decodeToString().trim()
            if (line.isNotEmpty()) onLine.invoke(line)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeNextDescriptor() {
        val next = descriptorQueue.poll() ?: return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // 33+ 권장 방식
                gatt?.writeDescriptor(next, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                run {
                    next.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt?.writeDescriptor(next)
                }
            }
        } catch (_: SecurityException) {
            onError("Descriptor write 권한 오류")
        }
    }
}
