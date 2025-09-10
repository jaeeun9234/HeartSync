package com.example.HeartSync.data.remote

import com.google.firebase.firestore.FirebaseFirestore

class PpgRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /** CSV 5필드: s1_smoothed, s1_bpm, s2_smoothed, s2_bpm, delta */
    fun saveCsv5(line: String, deviceAddress: String?) {
        val parts = line.split(',')
        if (parts.size != 5) return

        val s1 = parts[0].toFloatOrNull() ?: return
        val b1 = parts[1].toFloatOrNull() ?: return
        val s2 = parts[2].toFloatOrNull() ?: return
        val b2 = parts[3].toFloatOrNull() ?: return
        val d  = parts[4].toFloatOrNull() ?: return

        val doc = hashMapOf(
            "s1_smoothed" to s1,
            "s1_bpm"      to b1,
            "s2_smoothed" to s2,
            "s2_bpm"      to b2,
            "delta"       to d,
            "device"      to deviceAddress,
            "timestamp"   to System.currentTimeMillis()
        )
        db.collection("ppgData").add(doc)
    }
}
