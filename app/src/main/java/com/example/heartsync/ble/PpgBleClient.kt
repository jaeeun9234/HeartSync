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

/**
 * PpgBleClient
 * - 스캔/연결/Notify/명령 Write 를 모두 포함한 단일 클라이언트
 * - UI: StateFlow(scanning/scanResults/connectionState) 구독
 * - Service: onLine / onConnected / onError 콜백 사용 가능
 *
 * ✨ 필요 권한:
 *  - Android 12+ : BLUETOOTH_SCAN, BLUETOOTH_CONNECT
 *  - Android 11- : ACCESS_FINE_LOCATION (스캔용)
 */
class PpgBleClient(
    private val ctx: Context,
    private val onLine: (String) -> Unit = {},
    private val onConnected: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {

    // ====== ⛓️ UUID (실제 기기로 교체 필요) ======
    private val SERVICE_UUID = UUID.fromString("0000aaaa-0000-1000-8000-00805f9b34fb")
    private val CHAR_DATA_UUID = UUID.fromString("0000aaab-0000-1000-8000-00805f9b34fb") // Notify
    private val CHAR_CMD_UUID  = UUID.fromString("0000aaac-0000-1000-8000-00805f9b34fb") // Write
    private val CCCD_UUID      = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ====== 🔄 UI State ======
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults.asStateFlow()

    sealed interface ConnectionState {
        data object Disconnected : ConnectionState
        data object Connecting   : ConnectionState
        data class Connected(val device: BleDevice) : ConnectionState
        data class Failed(val reason: String) : ConnectionState
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ====== 🔧 BLE Handles ======
    private val btManager: BluetoothManager? get() =
        ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val btAdapter: BluetoothAdapter? get() = btManager?.adapter
    private val scanner: BluetoothLeScanner? get() = btAdapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var cmdChar: BluetoothGattCharacteristic? = null

    private var scanCallback: ScanCallback? = null

    // ====== ✅ Permission Helpers ======
    private fun hasScanPerm(): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasConnectPerm(): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasLegacyLocation(): Boolean =
        Build.VERSION.SDK_INT < 31 && ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    // ====== 🔍 Scan ======
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (btAdapter?.isEnabled != true) {
            onError("블루투스가 꺼져 있습니다.")
            return
        }
        if (!(hasScanPerm() || hasLegacyLocation())) {
            onError("스캔 권한이 없습니다.")
            return
        }
        if (_scanning.value) return

        _scanResults.value = emptyList()
        _scanning.value = true

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID)) // 서비스 UUID로 필터링
                .build()
        )
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
        scanCallback?.let { cb -> scanner?.stopScan(cb) }
        scanCallback = null
    }

    // ====== 🔗 Connect ======
    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        if (!hasConnectPerm()) {
            onError("연결 권한이 없습니다.")
            return
        }
        stopScan()
        _connectionState.value = ConnectionState.Connecting

        val btDev = try {
            BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device.address)
        } catch (e: IllegalArgumentException) {
            _connectionState.value = ConnectionState.Failed("잘못된 MAC 주소")
            return
        }

        gatt = btDev.connectGatt(ctx, /* autoConnect */ false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            if (hasConnectPerm()) {
                gatt?.disconnect()
                gatt?.close()
            }
        } catch (_: SecurityException) {
        } finally {
            gatt = null
            dataChar = null
            cmdChar  = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    // ====== ✉️ Write command ======
    @SuppressLint("MissingPermission")
    fun writeCmd(text: String) {
        val c = cmdChar ?: return
        if (!hasConnectPerm()) return
        val value = text.toByteArray()

        if (Build.VERSION.SDK_INT >= 33) {
            gatt?.writeCharacteristic(
                c,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            c.value = value
            @Suppress("DEPRECATION")
            gatt?.writeCharacteristic(c)
        }
    }

    // ====== 🧬 Callback ======
    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Failed("GATT 오류: $status")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!hasConnectPerm()) return
                    // 서비스 검색 시작
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
                _connectionState.value = ConnectionState.Failed("서비스 검색 실패: $status")
                return
            }
            // 서비스/특성 찾기
            val svc = gatt.getService(SERVICE_UUID) ?: run {
                _connectionState.value = ConnectionState.Failed("서비스 UUID 미일치")
                return
            }
            dataChar = svc.getCharacteristic(CHAR_DATA_UUID)
            cmdChar  = svc.getCharacteristic(CHAR_CMD_UUID)

            if (dataChar == null || cmdChar == null) {
                _connectionState.value = ConnectionState.Failed("특성 UUID 미일치")
                return
            }

            // Notify 활성화
            gatt.setCharacteristicNotification(dataChar, true)
            val cccd = dataChar!!.getDescriptor(CCCD_UUID)
            cccd?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it) // 일부 기기는 이 단계가 필수
            }

            // 연결 완료로 전환
            val dev = BleDevice(gatt.device?.name, gatt.device?.address ?: "Unknown")
            _connectionState.value = ConnectionState.Connected(dev)
            onConnected.invoke()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHAR_DATA_UUID) {
                val bytes = characteristic.value ?: return
                val line = bytes.decodeToString().trim()
                if (line.isNotEmpty()) onLine.invoke(line)
            }
        }
    }

    // ====== 🪄 서비스에서 쓰기 좋은 래핑 ======
    /**
     * 스캔 → 첫 결과 자동 선택 → 연결 → 서비스/Notify 셋업
     * 실제 장치가 하나뿐이거나, 특정 이름/주소 기준으로 고르려면 아래 TODO를 채우세요.
     */
    @SuppressLint("MissingPermission")
    fun connectAndSubscribe() {
        // 간단 예시: 바로 스캔 시작해서 첫 결과로 연결
        startScan()
        // TODO: 실사용에서는 스캔 콜백에서 원하는 기기를 선택해 connect() 호출하세요.
        // 여기서는 데모로 2초 후 첫 항목 연결 정도를 구현해도 되고,
        // MeasureService에서 직접 connect(BleDevice)를 호출하는 흐름으로 바꿔도 됩니다.
    }
}
