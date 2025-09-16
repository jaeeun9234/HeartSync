// app/src/main/java/com/example/heartsync/service/MeasureService.kt
package com.example.heartsync.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.heartsync.data.model.PpgEvent
import com.example.heartsync.data.remote.PpgRepository
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.data.model.BleDevice
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
// ★ 불필요: kotlinx.coroutines.flow.collect import 제거
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

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

    // 로그인 필수 (anonymous 금지)
    private var userId: String = ""
    private var sessionId: String = ""

    override fun onCreate() {
        super.onCreate()

        // 포그라운드 알림 채널 보장
        NotificationHelper.ensureChannel(this, NOTI_CHANNEL_ID, "HeartSync Measure")

        // 초기 알림
        val noti = NotificationCompat.Builder(this, NOTI_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("측정 중")
            .setContentText("BLE 연결 및 수집 준비")
            .setOngoing(true)
            .build()
        startForeground(NOTI_ID, noti)

        // Firestore Repo / Auth 초기화
        repo = PpgRepository(FirebaseFirestore.getInstance())

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("MeasureService", "User not logged in, stopping service")
            stopSelf()
            return
        }
        userId = user.uid

        // 세션 ID 생성 (UTC ISO + 8자리 UUID)
        sessionId = newSessionId()

        MeasureStatusBus.setMeasuring(true)
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
                scope.launch(Dispatchers.IO) { handleLine(line) }
            },
            onError = { e ->
                Log.e("MeasureService", "BLE error: $e")
            },
            filterByService = true
        )

        // 연결 시도
        client.connect(BleDevice(devName, devAddr))

        // 연결 상태에 따라 알림 텍스트 업데이트
        scope.launch {
            client.connectionState.collect { st ->
                val connected = st is PpgBleClient.ConnectionState.Connected
                MeasureStatusBus.setConnected(connected)

                // ★ 삭제: requestMtu/enableNotify/writeCommand 호출 (PpgBleClient 내부에서 수행)
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
        MeasureStatusBus.setMeasuring(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------
    // 헬퍼
    // -------------------------------

    private fun newSessionId(): String {
        val iso = OffsetDateTime.now(ZoneOffset.UTC).toString().replace(":", "-")
        val suffix = UUID.randomUUID().toString().take(8)
        return "${iso}_$suffix"
    }

    private fun utcIsoNow(): String =
        OffsetDateTime.now(ZoneOffset.UTC).toString()

    // CSV 한 줄 → Firestore 업로드
    private suspend fun handleLine(line: String) {
        val ev = parseCsvLineToEvent(line) ?: return

        // UI용 스트림 (선택)
        ev.smoothed_left?.lastOrNull()?.let { l ->
            ev.smoothed_right?.lastOrNull()?.let { r ->
                PpgRepository.emitSmoothed(l, r)
            }
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            repo.uploadRecord(uid, sessionId, ev)
        } catch (t: Throwable) {
            Log.e("MeasureService", "uploadRecord failed", t)
        }
    }

    private fun parseCsvLineToEvent(line: String): PpgEvent? {
        val raw = line.trim()
        if (raw.isEmpty()) return null
        if (raw.startsWith("#")) return null
        if (raw.lowercase().startsWith("event,")) return null

        val parts = raw.split(",").map { it.trim() }.toMutableList()
        while (parts.size < 19) parts.add("")

        val event       = parts[0].ifBlank { "STAT" }.uppercase()
        val hostTimeIso = parts[1].ifBlank { utcIsoNow() }
        val tsMs        = parts[2].toLongOrNull() ?: 0L
        val alertType   = parts[3].ifBlank { null }
        val reasons     = parseReasons(parts[4])

        fun D(s: String) = s.removePrefix("[").removeSuffix("]").takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        fun I(s: String) = s.takeIf { it.isNotEmpty() }?.toIntOrNull()
        fun listD(s: String): List<Double>? {
            if (s.isBlank()) return null
            val t = s.removePrefix("[").removeSuffix("]")
            val toks = t.split(',', ';', '|').map { it.trim() }.filter { it.isNotEmpty() }
            if (toks.isEmpty()) return null
            val vals = toks.mapNotNull { it.toDoubleOrNull() }
            return if (vals.isEmpty()) null else vals
        }
        fun sideNorm(s: String): String? {
            val v = s.trim().lowercase()
            return when (v) { "left", "right", "balance" -> v; else -> null }
        }

        return PpgEvent(
            event = event,
            host_time_iso = hostTimeIso,
            ts_ms = tsMs,
            alert_type = alertType,
            reasons = reasons,
            AmpRatio = D(parts[5]),
            PAD_ms = D(parts[6]),
            dSUT_ms = D(parts[7]),
            ampL = D(parts[8]),
            ampR = D(parts[9]),
            SUTL_ms = D(parts[10]),
            SUTR_ms = D(parts[11]),
            BPM_L = D(parts[12]),
            BPM_R = D(parts[13]),
            PQIL = I(parts[14]),
            PQIR = I(parts[15]),
            side = sideNorm(parts[16]),
            smoothed_left = listD(parts[17]),
            smoothed_right = listD(parts[18])
        )
    }

    private fun parseReasons(s: String?): List<String>? {
        if (s.isNullOrBlank()) return null
        val list = s.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        return if (list.isEmpty()) null else list
    }
}
