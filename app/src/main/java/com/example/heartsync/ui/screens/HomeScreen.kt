package com.example.heartsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.heartsync.viewmodel.BleViewModel

@Composable
fun HomeScreen(
    onClickBle: () -> Unit,
    bleVm: BleViewModel = viewModel()
) {
    val ble by bleVm.state.collectAsState()
    val banner = if (ble.isConnected) "기기가 연결되어 있습니다." else "기기 연결이 필요합니다."

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(banner)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onClickBle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (ble.isConnected) "연결 상태 보기" else "기기 연결")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ↓ 여기 아래는 기존의 카드(날짜/그래프/측정 시작 등)를 두면 사진 4와 동일하게 보이게 됨
        // 측정 시작 버튼, 그래프, 통계 등...
    }
}
