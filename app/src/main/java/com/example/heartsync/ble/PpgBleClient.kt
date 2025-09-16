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
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.heartsync.data.model.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * HeartSync BLE 클라이언트
 * - GATT 133 회피: 연결 순서 정석화 (discover → MTU → CCCD), 완전 종료, (선택) 캐시 refresh
 */
class PpgBleClient(
    private val ctx: Context,
    private val onLine: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val filterByService: Boolean = false
) {
    // ===== UUID =====
    private val serviceUuid: UUID = UUID.fromString("5ba7a52c-c3fe-46eb-8ade-0dacbd466278")
    private val notifyCharUuids = listOf(
        UUID.fromString("5dde726d-4cf3-4e2f-ab24-323caa359b78")
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
    private val descriptorQueue = LinkedBlockingQueue<BluetoothGattDescriptor>()
    private var scanCallback: ScanCallback? = null

    // ★ 추가: Notify 조각들을 \n 기준으로 한 줄로 합치는 버퍼
    private val lineBuf = StringBuilder()

    // ★ 추가: 바이트 스트림 → 한 줄씩 콜백으로 전달
    private fun feedBytesAndEmitLines(bytes: ByteArray) {
        val s = try { bytes.toString(Charsets.UTF_8) } catch (_: Exception) { return }
        lineBuf.append(s)
        while (true) {
            val idx = lineBuf.indexOf("\n")
            if (idx < 0) break
            val line = lineBuf.substring(0, idx).trimEnd('\r')
            lineBuf.delete(0, idx + 1)
            if (line.isNotBlank()) onLine(line)
        }
    }

    // 재시도용
    private var targetDevice: BleDevice? = null
    private var backoffMs = 1500

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
        targetDevice = device
        _connectionState.value = ConnectionState.Connecting

        val btDev = try {
            BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            _connectionState.value = ConnectionState.Failed("잘못된 MAC 주소"); return
        }

        closeGatt() // 이전 세션 완전 정리
        Log.d("BLE", "connect() to ${device.address}")

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            btDev.connectGatt(ctx, /*autoConnect*/ false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            btDev.connectGatt(ctx, /*autoConnect*/ false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d("BLE", "disconnect()")
        closeGatt()
        _connectionState.value = ConnectionState.Disconnected
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        if (!hasConnectPerm()) {
            gatt = null
            return
        }
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
    }

    private fun refreshDeviceCache(g: BluetoothGatt): Boolean {
        return try {
            val m = g.javaClass.getMethod("refresh")
            m.isAccessible = true
            m.invoke(g) as Boolean
        } catch (_: Exception) { false }
    }

    private fun retryConnectWithBackoff() {
        val dev = targetDevice ?: return
        val d = backoffMs.coerceAtMost(5000)
        Log.d("BLE", "retry in ${d}ms")
        android.os.Handler(ctx.mainLooper).postDelayed({
            backoffMs = (backoffMs * 2).coerceAtMost(5000)
            connect(dev)
        }, d.toLong())
    }

    // ===== GATT Callback =====
    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BLE", "onConnChange status=$status state=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                refreshDeviceCache(g)
                closeGatt()
                _connectionState.value = ConnectionState.Failed("GATT 오류: $status")
                retryConnectWithBackoff()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    backoffMs = 1500
                    // 서비스 검색 먼저(여기서 MTU 요청 금지)
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt()
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.d("BLE", "onServicesDiscovered status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Failed("서비스 검색 실패: $status"); return
            }

            val svc = g.getService(this@PpgBleClient.serviceUuid)
            if (svc == null) { _connectionState.value = ConnectionState.Failed("서비스 UUID 미일치"); return }

            // 연결 성공 상태 업데이트
            val dev = BleDevice(g.device?.name, g.device?.address ?: "Unknown")
            _connectionState.value = ConnectionState.Connected(dev)

            // MTU 먼저 요청 → onMtuChanged에서 CCCD 진행
            val ok = g.requestMtu(185)
            if (!ok) {
                // 일부 단말은 false 반환해도 콜백이 올 수 있음 → 안전하게 바로 알림 등록 시도
                enableNotifications(g, svc)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("BLE", "onMtuChanged mtu=$mtu status=$status")
            val svc = g.getService(this@PpgBleClient.serviceUuid) ?: run {
                if (hasConnectPerm()) g.disconnect()
                return
            }
            enableNotifications(g, svc)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d("BLE", "onDescriptorWrite status=$status")
            writeNextDescriptor()
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            val bytes = ch.value ?: return
            // ★ 수정: 조각을 바로 문자열로 trim하지 말고, 라인 프레이머로 누적 후 \n 단위로 전달
            feedBytesAndEmitLines(bytes)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, svc: BluetoothGattService) {
        // 여러 특성에 대해 순차적으로 CCCD 등록 (큐 사용)
        descriptorQueue.clear()
        for (uuid in notifyCharUuids) {
            val ch = svc.getCharacteristic(uuid) ?: continue
            val ok = try { g.setCharacteristicNotification(ch, true) } catch (_: SecurityException) { false }
            if (!ok) continue
            val cccd = ch.getDescriptor(cccdUuid) ?: continue
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            descriptorQueue.add(cccd)
        }
        writeNextDescriptor()
    }

    @SuppressLint("MissingPermission")
    private fun writeNextDescriptor() {
        val d = descriptorQueue.poll() ?: return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                gatt?.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                run {
                    d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt?.writeDescriptor(d)
                }
            }
        } catch (_: SecurityException) {
            onError("Descriptor write 권한 오류")
        }
    }
}
