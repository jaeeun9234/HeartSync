package com.example.heartsync.data.remote

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirestoreUploader(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val uidProvider: () -> String = { "demo-uid" },       // TODO: FirebaseAuth 연동
    private val sessionIdProvider: () -> String = { System.currentTimeMillis().toString() }
) {
    fun batchInsert(samples: List<Map<String, Any?>>) {
        scope.launch {
            val uid = uidProvider()
            val sid = sessionIdProvider()
            val db = Firebase.firestore
            val col = db.collection("users").document(uid)
                .collection("sessions").document(sid)
                .collection("samples")
            db.runBatch { b ->
                samples.forEach { b.set(col.document(), it) }
            }.addOnFailureListener { it.printStackTrace() }
        }
    }
}
