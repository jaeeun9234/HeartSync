package com.example.heartsync.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.data.local.CsvWriter
import com.example.heartsync.data.model.SessionConfig
import com.example.heartsync.data.remote.FirestoreUploader
import com.example.heartsync.util.CSV_HEADER

class MeasureService : Service() {

    companion object {
        const val EXTRA_CFG = "cfg"
        private const val NOTI_ID = 10
    }

    private lateinit var cfg: SessionConfig
    private lateinit var ble: PpgBleClient
    private lateinit var csv: CsvWriter
    private lateinit var fs: FirestoreUploader

    private val batch = mutableListOf<Map<String, Any?>>()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        startForeground(NOTI_ID, NotificationHelper.build(this, "Preparing…"))

        csv = CsvWriter(this, subDir = "HeartSync")
        fs = FirestoreUploader()
        ble = PpgBleClient(this) { line -> onBleLine(line) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cfg = intent?.getParcelableExtra(EXTRA_CFG) ?: return START_NOT_STICKY

        csv.openNewFile(header = CSV_HEADER)
        ble.connectAndSubscribe()

        // ESP32에 측정 명령 전달(예: START,60)
        ble.writeCmd("START,${cfg.durationSec}")

        // duration 끝나면 종료
        main.postDelayed({ stopSession() }, cfg.durationSec * 1000L)

        updateNotification("Measuring ${cfg.durationSec}s…")
        return START_STICKY
    }

    private fun updateNotification(text: String) {
        val noti = NotificationHelper.build(this, text)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTI_ID, noti)
    }

    private fun onBleLine(line: String) {
        // 1) CSV 기록
        csv.append(line)

        // 2) Firestore 배치 버퍼
        parseToMap(line)?.let { sample ->
            batch += sample
            if (batch.size >= cfg.batchSize) {
                fs.batchInsert(batch)
                batch.clear()
            }
        }
    }

    private fun parseToMap(line: String): Map<String, Any?>? {
        // 포맷: timestamp,s1,s1_bpm,s2,s2_bpm,delta,status
        val p = line.split(',')
        if (p.size < 7) return null
        return mapOf(
            "timestamp" to p[0],
            "sensor1_smoothed" to p[1].toDoubleOrNull(),
            "sensor1_bpm" to p[2].toDoubleOrNull(),
            "sensor2_smoothed" to p[3].toDoubleOrNull(),
            "sensor2_bpm" to p[4].toDoubleOrNull(),
            "delta" to p[5].toDoubleOrNull(),
            "status" to p[6]
        )
    }

    private fun stopSession() {
        ble.writeCmd("STOP")
        if (batch.isNotEmpty()) {
            fs.batchInsert(batch)
            batch.clear()
        }
        csv.closeCurrentFile()
        updateNotification("Saved CSV & uploaded")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        ble.disconnect()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
