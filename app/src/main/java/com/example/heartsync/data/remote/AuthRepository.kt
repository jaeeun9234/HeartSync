package com.example.heartsync.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUser get() = auth.currentUser

    suspend fun isIdAvailable(id: String): Boolean {
        val snap = db.collection("users").whereEqualTo("id", id).limit(1).get().await()
        return snap.isEmpty
    }

    // ✅ ID + PW 로그인
    suspend fun loginWithId(id: String, password: String) {
        // 1) id → email 조회
        val snap = db.collection("users").whereEqualTo("id", id).limit(1).get().await()
        if (snap.isEmpty) throw IllegalStateException("해당 ID가 없습니다.")
        val email = snap.documents.first().getString("email")
            ?: throw IllegalStateException("이 ID에 연결된 이메일이 없습니다.")

        // 2) Firebase Auth 로그인
        auth.signInWithEmailAndPassword(email, password).await()

        // 3) updatedAt 갱신(선택)
        currentUser?.uid?.let { uid ->
            db.collection("users").document(uid)
                .update("updatedAt", FieldValue.serverTimestamp()).await()
        }
    }

    suspend fun register(
        id: String, name: String, phone: String, birth: String, email: String, password: String
    ) {
        if (!isIdAvailable(id)) throw IllegalStateException("이미 존재하는 ID입니다.")
        val res = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = res.user?.uid ?: error("UID null")

        val doc = hashMapOf(
            "uid" to uid, "id" to id, "name" to name, "phone" to phone, "email" to email, "birth" to birth,
            "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(doc).await()
    }

    fun logout() = auth.signOut()
}
