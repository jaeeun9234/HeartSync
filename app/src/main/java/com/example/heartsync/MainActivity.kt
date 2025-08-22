package com.example.heartsync

import com.example.heartsync.ui.screens.LoginScreen
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.heartsync.data.model.MeasureMode
import com.example.heartsync.data.model.SessionConfig
import com.example.heartsync.service.MeasureService
import com.example.heartsync.ui.screens.HomeScreen
import com.example.heartsync.ui.screens.LoginScreen

class MainActivity : ComponentActivity() {
    private var keepSplash = true

    private val permissionLauncher = registerForActivityResult(RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1) 스플래시 설치 
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition {keepSplash}
        
        super.onCreate(savedInstanceState)
        requestRuntimePerms()

        // ex. 1.5초 동안 유지 후 false로 전환
        window.decorView.postDelayed({
            keepSplash = false
        }, 1500)
        
        // 2) 메인 콘텐츠
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    AppContent()
                }
            }
        }
    }

    @Composable
    private fun AppContent() {
        // 지금은 “UI 틀만”이므로 메모리 상태로 로그인 여부를 관리
        var isLoggedIn by remember { mutableStateOf(false) }

        if (isLoggedIn) {
            HomeScreen(
                onStart60s = {
                    val cfg = SessionConfig(mode = MeasureMode.SPOT, durationSec = 60)
                    startMeasureService(cfg)
                },
                onStop = {
                    stopService(Intent(this, MeasureService::class.java))
                }
            )
        } else {
            LoginScreen(
                onLoginClick = { id, pw ->
                    // FIXME: 나중에 Firebase Auth 연결
                    // signInWithEmailAndPassword(id, pw) 성공 시 isLoggedIn = true
                    isLoggedIn = true
                },
                onRegisterClick = {
                    // FIXME: 나중에 회원가입 화면 연결 (NavHost 사용 권장)
                }
            )
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