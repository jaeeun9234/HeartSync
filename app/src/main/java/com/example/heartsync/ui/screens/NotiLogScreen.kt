// app/src/main/java/com/example/heartsync/ui/screens/NotiLogScreen.kt
package com.example.heartsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NotiLogScreen() {
    // TODO: 나중에 Firestore/로컬 DB 연동해 실제 알림 로그로 교체
    val dummy = remember {
        listOf(
            "2025-09-10 09:12 · SpO₂ 낮음",
            "2025-09-10 09:07 · BPM 높음",
            "2025-09-09 22:14 · 낙상 감지",
        )
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("이상 알림 로그", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dummy) { row ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text(row, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
