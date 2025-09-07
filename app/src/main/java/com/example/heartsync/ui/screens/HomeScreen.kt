// app/src/main/java/com/example/heartsync/ui/screens/HomeScreen.kt
package com.example.heartsync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.viewmodel.BleViewModel
import com.example.heartsync.ui.components.StatusCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    onClickBle: () -> Unit,
    bleVm: BleViewModel   // MainActivity에서 주입 (전역 공유)
) {
    val conn by bleVm.connectionState.collectAsState()
    val isConnected = conn is PpgBleClient.ConnectionState.Connected
    val deviceName = (conn as? PpgBleClient.ConnectionState.Connected)?.device?.name ?: "Unknown"

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 날짜 칩
        DateChip()

        // 상태 배너
        StatusCard(
            icon = if (isConnected) "success" else "error",
            title = if (isConnected) "기기가 연결되어 있습니다." else "기기 연결이 필요합니다.",
            buttonText = if (isConnected) "연결 상태 보기" else "기기 연결",
            onClick = onClickBle
        )

        // (예시) 간단한 더미 그래프/수치 영역 – 기존 위젯으로 대체 가능
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("그래프 자리", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(120.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("BPM: 89")
                    Text("혈압: 120/80 mmHg")
                }
            }
        }

        Button(
            onClick = { /* 측정 시작 로직 연결 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = isConnected
        ) {
            Text("측정 시작")
        }
    }
}

@Composable
private fun DateChip() {
    val today = remember {
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        sdf.format(Date())
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = today,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}