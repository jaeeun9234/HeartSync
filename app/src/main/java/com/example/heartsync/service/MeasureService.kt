package com.example.heartsync.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.HeartSync.data.remote.PpgRepository
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.data.model.BleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class MeasureService : Service() {

    companion object {
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_DEVICE_ADDR = "extra_device_addr"
        const val NOTI_ID = 1001
        const val NOTI_CHANNEL_ID = "measuresvc"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var client: PpgBleClient
    private lateinit var repo: PpgRepository
    // private lateinit var csvWriter: CsvWriter // ← 시그니처 맞추기 전까진 비활성화

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this, NOTI_CHANNEL_ID, "HeartSync Measure")

        // 아이콘은 임시로 시스템 블루투스 아이콘 사용
        val noti = NotificationCompat.Builder(this, NOTI_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("측정 중")
            .setContentText("BLE 연결 및 수집 준비")
            .setOngoing(true)
            .build()
        startForeground(NOTI_ID, noti)

        repo = PpgRepository()
        // csvWriter = CsvWriter(this, subDir = "HeartSync", filePrefix = "ppg_") // ← 네 시그니처에 맞춰 나중에 복구
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val devName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
        val devAddr = intent?.getStringExtra(EXTRA_DEVICE_ADDR)

        if (devAddr.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        client = PpgBleClient(
            ctx = this,
            onLine = { line ->
                // Firestore 저장
                repo.saveCsv5(line, devAddr)
                // csvWriter.appendLine(line) // ← 나중에 복구
            },
            onError = { /* TODO: 알림 업데이트/로그 */ },
            filterByService = true
        )

        // 연결 시도
        val device = BleDevice(devName, devAddr)
        client.connect(device)

        // 상태 변화 → 알림 텍스트 업데이트
        scope.launch {
            client.connectionState.collect { st: PpgBleClient.ConnectionState ->
                val text = when (st) {
                    is PpgBleClient.ConnectionState.Connected  -> "연결됨: ${st.device.address}"
                    is PpgBleClient.ConnectionState.Connecting -> "연결 중…"
                    is PpgBleClient.ConnectionState.Failed     -> "실패: ${st.reason}"
                    else -> "대기"
                }
                val n = NotificationCompat.Builder(this@MeasureService, NOTI_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentTitle("측정 중")
                    .setContentText(text)
                    .setOngoing(true)
                    .build()
                startForeground(NOTI_ID, n)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        runCatching { client.disconnect() }
        // runCatching { csvWriter.close() } // ← 네 시그니처 확인 후 복구
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
