package com.example.heartsync

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.heartsync.data.model.MeasureMode
import com.example.heartsync.data.model.SessionConfig
import com.example.heartsync.service.MeasureService

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePerms()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    HomeScreen(
                        onStart60s = {
                            val cfg = SessionConfig(mode = MeasureMode.SPOT, durationSec = 60)
                            startMeasureService(cfg)
                        },
                        onStop = {
                            stopService(Intent(this, MeasureService::class.java))
                        }
                    )
                }
            }
        }
    }

    private fun requestRuntimePerms() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    private fun startMeasureService(cfg: SessionConfig) {
        val intent = Intent(this, MeasureService::class.java).apply {
            putExtra(MeasureService.EXTRA_CFG, cfg)
        }
        ContextCompat.startForegroundService(this, intent)
    }
}

@Composable
private fun HomeScreen(onStart60s: () -> Unit, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("HeartSync • Spot Measure (60s)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart60s, modifier = Modifier.fillMaxWidth()) {
            Text("Start 60s Measurement")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text("Stop")
        }
    }
}
