// app/src/main/java/com/example/heartsync/service/MeasureService.kt
package com.example.heartsync.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.heartsync.data.model.PpgEvent
import com.example.heartsync.data.remote.PpgRepository
import com.example.heartsync.service.MeasureStatusBus
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.data.model.BleDevice
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
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

    private var userId: String = "anonymous"
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
        val firestore = FirebaseFirestore.getInstance()
        repo = PpgRepository(firestore)
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

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
                // BLE 1줄 수신 → CSV 파싱 → Firestore 업로드
                scope.launch(Dispatchers.IO) {
                    parseCsvLineToEvent(line)?.let { ev ->
                        val l = ev.smoothed_left?.lastOrNull()
                        val r = ev.smoothed_right?.lastOrNull()
                        if (l != null && r != null) {
                            // PpgRepository의 companion object 스트림 허브로 직접 emit
                            com.example.heartsync.data.remote.PpgRepository.emitSmoothed(l, r)
                        }

                        runCatching {
                            repo.uploadRecord(userId, sessionId, ev)
                        }.onFailure {
                            // TODO: 로깅/재시도 큐 적재 등
                        }
                    }
                }
            },
            onError = {
                // TODO: 알림/로그 처리
            },
            filterByService = true
        )

        // 연결 시도
        val device = BleDevice(devName, devAddr)
        client.connect(device)

        // 연결 상태에 따라 알림 텍스트 업데이트
        scope.launch {
            client.connectionState.collect { st: PpgBleClient.ConnectionState ->
                // ✅ 추가: 연결 여부 UI로 올림
                val connected = st is PpgBleClient.ConnectionState.Connected
                MeasureStatusBus.setConnected(connected)

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

    /**
     * CSV 파서
     * ESP32가 아래 순서로 보낸다고 가정:
     * event,host_time_iso,ts_ms,alert_type,reasons,AmpRatio,PAD_ms,dSUT_ms,ampL,ampR,
     * SUTL_ms,SUTR_ms,BPM_L,BPM_R,PQIL,PQIR,side,smoothed_left,smoothed_right
     *
     * - reasons: "AmpRatio low; PAD high" (세미콜론 구분 권장)
     * - smoothed_*: "0.12,0.13,0.15" 또는 세미콜론/파이프 허용, 대괄호 유무 허용
     * - 헤더 라인("event,...") / 빈 줄 / 주석("#")은 스킵
     */
    private fun parseCsvLineToEvent(line: String): PpgEvent? {
        val raw = line.trim()
        if (raw.isEmpty()) return null
        if (raw.startsWith("#")) return null
        if (raw.lowercase().startsWith("event,")) return null

        // 단순 콤마 split (필요 시 따옴표-aware CSV 파서로 교체 가능)
        val parts = raw.split(",").map { it.trim() }.toMutableList()

        // 부족한 필드는 빈 문자열로 패딩 (총 19필드 가정)
        while (parts.size < 19) parts.add("")

        val event       = parts[0].ifBlank { "STAT" }.uppercase()
        val hostTimeIso = parts[1].ifBlank { utcIsoNow() }
        val tsMs        = parts[2].toLongOrNull() ?: 0L
        val alertType   = parts[3].ifBlank { null }
        val reasons     = parseReasons(parts[4])

        fun D(s: String) = s
            .removePrefix("[")
            .removeSuffix("]")
            .takeIf { it.isNotEmpty() }
            ?.toDoubleOrNull()

        fun I(s: String) = s.takeIf { it.isNotEmpty() }?.toIntOrNull()

        fun listD(s: String): List<Double>? {
            if (s.isBlank()) return null
            val t = s.removePrefix("[").removeSuffix("]")
            val toks = t.split(',', ';', '|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (toks.isEmpty()) return null
            val vals = toks.mapNotNull { it.toDoubleOrNull() }
            return if (vals.isEmpty()) null else vals
        }

        fun sideNorm(s: String): String? {
            val v = s.trim().lowercase()
            return when (v) {
                "left", "right", "balance" -> v
                else -> null
            }
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
        val list = s.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (list.isEmpty()) null else list
    }
}
