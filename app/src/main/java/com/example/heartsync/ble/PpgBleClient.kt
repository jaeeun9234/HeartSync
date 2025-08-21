package com.example.heartsync.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.os.ParcelUuid
import java.util.*

class PpgBleClient(
    private val ctx: Context,
    private val onLine: (String) -> Unit
) {
    // TODO: 실제 장치 UUID로 교체하세요
    private val SERVICE_UUID = UUID.fromString("0000aaaa-0000-1000-8000-00805f9b34fb")
    private val CHAR_DATA_UUID = UUID.fromString("0000aaab-0000-1000-8000-00805f9b34fb") // Notify
    private val CHAR_CMD_UUID  = UUID.fromString("0000aaac-0000-1000-8000-00805f9b34fb") // Write

    private var gatt: BluetoothGatt? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var cmdChar: BluetoothGattCharacteristic? = null

    fun connectAndSubscribe() {
        // 실제 구현: 스캔→필터(SERVICE_UUID)→connectGatt→onServicesDiscovered에서 캐릭터리스틱 찾기→notify 설정
        // 여기서는 최소 뼈대만 두고, 프로젝트에서 실제 BLE 코드 채워 넣도록 함
    }

    /** Android 12+(S)에서 BLUETOOTH_CONNECT 권한 보유 여부 */
    private fun hasConnectPerm(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

    /** 명령 쓰기 */
    @SuppressLint("MissingPermission") // 내부에서 직접 권한 체크함
    fun writeCmd(text: String) {
        val c = cmdChar ?: return
        if (!hasConnectPerm()) return

        val value = text.toByteArray()

        if (Build.VERSION.SDK_INT >= 33) {
            // Tiramisu+ 비권장 API 대체
            gatt?.writeCharacteristic(
                c,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            c.value = value
            gatt?.writeCharacteristic(c)    // API33에서 deprecated라 분기 처리
        }
    }

    /** 연결 정리 */
    @SuppressLint("MissingPermission") // 내부에서 직접 권한 체크함
    fun disconnect() {
        try {
            if (hasConnectPerm()) {
                // 권한이 있을 때만 호출
                gatt?.disconnect()
                gatt?.close()
            }
        } catch (_: SecurityException) {
            // 사용자가 권한 거부한 상태에서 호출된 경우 안전하게 무시
        } finally {
            gatt = null
        }
    }

    // 수신 콜백에서 한 줄 완성 시 onLine("timestamp,...") 호출하도록 구현
}
