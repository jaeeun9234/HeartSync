package com.example.heartsync.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUser get() = auth.currentUser

    // ✅ ID + 비밀번호 로그인
    suspend fun loginWithId(id: String, password: String) {
        // 1) Firestore에서 ID → Email 매핑
        val snap = db.collection("users")
            .whereEqualTo("id", id)
            .limit(1)
            .get()
            .await()

        if (snap.isEmpty) throw IllegalStateException("해당 ID가 없습니다.")

        val email = snap.documents.first().getString("email")
            ?: throw IllegalStateException("이 ID에 연결된 이메일이 없습니다.")

        // 2) FirebaseAuth 로그인
        auth.signInWithEmailAndPassword(email, password).await()
    }

    // ✅ FirebaseAuth 회원가입 (Auth 계정만 생성)
    suspend fun register(email: String, password: String): String {
        val res = auth.createUserWithEmailAndPassword(email, password).await()
        return res.user?.uid ?: error("UID is null")
    }

    fun logout() = auth.signOut()
}
