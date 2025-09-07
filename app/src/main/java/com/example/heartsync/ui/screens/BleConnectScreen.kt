// app/src/main/java/com/example/heartsync/ui/screens/BleConnectScreen.kt
package com.example.heartsync.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.heartsync.ble.PpgBleClient
import com.example.heartsync.data.model.BleDevice
import com.example.heartsync.viewmodel.BleViewModel
import com.example.heartsync.ui.components.StatusCard

@Composable
fun BleConnectScreen(
    vm: BleViewModel,                // 전역 VM 주입
    onConnected: (() -> Unit)? = null // 연결 즉시 홈으로 가고 싶으면 전달, 3번화면 보고 싶으면 null
) {
    val scanning by vm.scanning.collectAsState()
    val results by vm.scanResults.collectAsState()
    val conn by vm.connectionState.collectAsState()

    // 연결 성공 시 자동 이동을 원하면 콜백 실행
    LaunchedEffect(conn) {
        if (conn is PpgBleClient.ConnectionState.Connected) {
            onConnected?.invoke()
        }
    }

    // 권한 런처 준비
    val requiredPerms = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 버튼에서 이어서 사용 */ }

    // 장치 선택 상태 (2번 화면에서 한 항목을 고르고 "연결하기")
    var selected by remember { mutableStateOf<BleDevice?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("BLE 장치 연결", style = MaterialTheme.typography.headlineSmall)

        when (conn) {
            is PpgBleClient.ConnectionState.Connected -> {
                // 3번 화면
                StatusCard(
                    icon = "success",
                    title = "연결된 기기: ${(conn as PpgBleClient.ConnectionState.Connected).device.name ?: "Unknown"}",
                    buttonText = "연결 해제",
                    onClick = { vm.disconnect() }
                )
            }
            is PpgBleClient.ConnectionState.Connecting -> {
                StatusCard(
                    icon = "error",
                    title = "연결 중…",
                    buttonText = "취소",
                    onClick = { vm.disconnect() }
                )
            }
            is PpgBleClient.ConnectionState.Disconnected,
            is PpgBleClient.ConnectionState.Failed -> {
                // 2번 화면
                StatusCard(
                    icon = "error",
                    title = if (conn is PpgBleClient.ConnectionState.Failed)
                        "연결 실패: ${(conn as PpgBleClient.ConnectionState.Failed).reason}"
                    else
                        "연결된 기기가 없습니다.",
                    buttonText = if (scanning) "스캔 중지" else "스캔 시작",
                    onClick = {
                        if (scanning) vm.stopScan()
                        else {
                            // 권한 요청 후 스캔 시작
                            permLauncher.launch(requiredPerms)
                            vm.startScan()
                        }
                    }
                )

                // 스캔 결과 목록
                if (results.isNotEmpty()) {
                    Text("발견된 장치 (${results.size})", fontWeight = FontWeight.Bold)
                    DeviceRadioList(
                        items = results,
                        selected = selected,
                        onSelected = { selected = it }
                    )
                }

                // 연결하기 버튼 (선택된 항목이 있을 때만 활성화)
                Button(
                    onClick = { selected?.let(vm::connect) },
                    enabled = selected != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) { Text("연결하기") }
            }
        }
    }
}

@Composable
private fun DeviceRadioList(
    items: List<BleDevice>,
    selected: BleDevice?,
    onSelected: (BleDevice) -> Unit
) {
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .padding(8.dp)
        ) {
            items(items) { dev ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(dev) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected?.address == dev.address,
                        onClick = { onSelected(dev) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(dev.name ?: "Unknown")
                        Text(dev.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Divider()
            }
        }
    }
}
