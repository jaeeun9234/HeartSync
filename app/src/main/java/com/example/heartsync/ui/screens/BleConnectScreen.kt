package com.example.heartsync.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.heartsync.data.model.BleDevice
import com.example.heartsync.ble.PpgBleClient.ConnectionState
import com.example.heartsync.viewmodel.BleViewModel
import android.os.Build
import android.Manifest

@Composable
fun BleConnectScreen(
    vm: BleViewModel = viewModel(),
    onConnected: () -> Unit = {}
) {
    val state by vm.state.collectAsState()

    // 런타임 권한 런처
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (!granted.values.contains(false)) vm.startScan()
    }

    // 연결 완료 시 상위로 콜백 (홈으로 복귀 등)
    LaunchedEffect(state.connection) {
        if (state.connection is ConnectionState.Connected) onConnected()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("BLE 장치 연결", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        when (state.connection) {
            ConnectionState.Disconnected,
            is ConnectionState.Failed -> {
                StatusBox(text = "연결된 기기가 없습니다.")
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        } else {
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        launcher.launch(perms)
                    },
                    enabled = !state.scanning,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.scanning) "스캔 중..." else "스캔 시작") }

                Spacer(Modifier.height(12.dp))
                DeviceList(devices = state.devices, onClick = vm::connect)
            }
            ConnectionState.Connecting -> {
                StatusBox(text = "연결 중...")
            }
            is ConnectionState.Connected -> {
                StatusBox(text = "연결된 기기: ${state.connectedName ?: "알 수 없음"}")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = vm::disconnect,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("연결 해제") }
            }
        }
    }
}

@Composable
private fun StatusBox(text: String) {
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
        Text(text, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun DeviceList(devices: List<BleDevice>, onClick: (BleDevice) -> Unit) {
    LazyColumn {
        items(devices, key = { it.address }) { d ->
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onClick(d) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(d.name ?: "알 수 없는 기기")
                    Text(d.address, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
