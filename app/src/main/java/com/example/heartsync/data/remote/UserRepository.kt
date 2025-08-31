package com.example.heartsync.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // ✅ ID 중복 확인
    suspend fun isIdAvailable(id: String): Boolean {
        val snap = db.collection("users")
            .whereEqualTo("id", id)
            .limit(1)
            .get()
            .await()
        return snap.isEmpty
    }

    // ✅ 유저 프로필 생성
    suspend fun createUserProfile(
        uid: String,
        id: String,
        name: String,
        phone: String,
        birth: String,
        email: String
    ) {
        val doc = hashMapOf(
            "uid" to uid,
            "id" to id,
            "name" to name,
            "phone" to phone,
            "birth" to birth,
            "email" to email,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(doc).await()
    }

    // ✅ 유저 프로필 조회
    suspend fun getUserProfile(uid: String) =
        db.collection("users").document(uid).get().await()
}
